/*
 * Copyright (c) 2014 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.contrib.hds;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.io.WritableComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.api.Context.OperatorContext;
import com.datatorrent.api.Operator;
import com.datatorrent.common.util.NameableThreadFactory;
import com.datatorrent.common.util.Slice;
import com.datatorrent.contrib.hds.HDSFileAccess.HDSFileReader;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;

/**
 * Reader for historical data store.
 * Implements asynchronous read from backing files and query refresh.
 */
public class HDSReader implements Operator
{
  public static final String FNAME_WAL = "_WAL";
  public static final String FNAME_META = "_META";

  public static class HDSQuery
  {
    long bucketKey;
    byte[] key;
    int keepAliveCount;
    volatile byte[] result;
    boolean processed;
  }

  private static final Logger LOG = LoggerFactory.getLogger(HDSReader.class);

  protected final transient Kryo kryo = new Kryo();
  @NotNull
  protected Comparator<Slice> keyComparator = new DefaultKeyComparator();
  @Valid
  @NotNull
  protected HDSFileAccess store;

  public BucketMeta loadBucketMeta(long bucketKey)
  {
    BucketMeta bucketMeta = null;
    try {
      InputStream is = store.getInputStream(bucketKey, FNAME_META);
      bucketMeta = (BucketMeta)kryo.readClassAndObject(new Input(is));
      is.close();
    } catch (IOException e) {
      bucketMeta = new BucketMeta(keyComparator);
    }
    return bucketMeta;
  }

  /**
   * Map containing all current queries. Accessed by operator and reader threads.
   */
  private final ConcurrentMap<Slice, HDSQuery> queries = Maps.newConcurrentMap();
  private final Map<Long, BucketReader> buckets = Maps.newHashMap();

  @VisibleForTesting
  protected transient ExecutorService queryExecutor;
  private volatile transient Exception executorError;

  public HDSReader()
  {
  }

  /**
   * Compare keys for sequencing as secondary level of organization within buckets.
   * In most cases it will be implemented using a time stamp as leading component.
   * @return
   */
  public Comparator<Slice> getKeyComparator()
  {
    return keyComparator;
  }

  public void setKeyComparator(Comparator<Slice> keyComparator)
  {
    this.keyComparator = keyComparator;
  }


  public HDSFileAccess getFileStore()
  {
    return store;
  }

  public void setFileStore(HDSFileAccess fileStore)
  {
    this.store = fileStore;
  }

  @Override
  public void setup(OperatorContext context)
  {
    this.store.init();
    if (queryExecutor == null) {
      queryExecutor = Executors.newSingleThreadScheduledExecutor(new NameableThreadFactory(this.getClass().getSimpleName()+"-Reader"));
    }
  }

  @Override
  public void teardown()
  {
    for (BucketReader bucket : this.buckets.values()) {
      for (HDSFileReader reader : bucket.readers.values()) {
        IOUtils.closeQuietly(reader);
      }
    }
    IOUtils.closeQuietly(store);
    queryExecutor.shutdown();
  }

  @Override
  public void beginWindow(long windowId)
  {
  }

  @Override
  public void endWindow()
  {
    // TODO: expire queries
    for (HDSQuery query : queries.values())
    {
      if (!query.processed) {
        processQuery(query);
      } else {
        emitQueryResult(query);
      }
    }
    if (executorError != null) {
      throw new RuntimeException("Error processing queries.", this.executorError);
    }
  }

  /**
   * Fetch result for the given query from persistent storage
   * Subclass can override this to serve from write cache.
   */
  protected void processQuery(final HDSQuery query)
  {
    Runnable readerRunnable = new Runnable() {
      @Override
      public void run()
      {
        try {
          query.result = get(query.bucketKey, query.key);
          query.processed = true;
        } catch (Exception e) {
          executorError = e;
        }
      }
    };
    this.queryExecutor.execute(readerRunnable);
  }

  protected BucketReader getReader(long bucketKey)
  {
    BucketReader br = this.buckets.get(bucketKey);
    if (br == null) {
      LOG.debug("Opening bucket {}", bucketKey);
      br = new BucketReader();
      br.bucketMeta = loadBucketMeta(bucketKey);
      this.buckets.put(bucketKey, br);
    }
    return br;
  }

  protected void invalidateReader(long bucketKey, String name)
  {
    BucketReader bucket = this.buckets.get(bucketKey);
    if (bucket != null) {
      IOUtils.closeQuietly(bucket.readers.remove(name));
    }
  }

  protected byte[] get(long bucketKey, byte[] key) throws IOException
  {
    Slice keyWrapper = new Slice(key, 0, key.length);

    BucketReader bucket = getReader(bucketKey);
    Map.Entry<Slice, BucketFileMeta> floorEntry = bucket.bucketMeta.files.floorEntry(keyWrapper);
    if (floorEntry == null) {
      // no file for this key
      return null;
    }

    // lookup against data file
    HDSFileReader reader = bucket.readers.get(floorEntry.getValue().name);
    if (reader == null) {
      bucket.readers.put(floorEntry.getValue().name, reader = store.getReader(bucketKey, floorEntry.getValue().name));
    }

    reader.seek(key);
    Slice value = new Slice(null, 0,0);
    reader.next(new Slice(null, 0, 0), value);
    return value.buffer;
  }

  protected void addQuery(HDSQuery query)
  {
    Slice key = HDSBucketManager.toSlice(query.key);
    HDSQuery existingQuery = this.queries.get(key);
    if (existingQuery != null) {
      query.keepAliveCount = Math.max(query.keepAliveCount, existingQuery.keepAliveCount);
    }
    this.queries.put(key, query);
  }

  protected void emitQueryResult(HDSQuery query)
  {
  }

  /**
   * Default key comparator that performs lexicographical comparison of the byte arrays.
   */
  public static class DefaultKeyComparator implements Comparator<Slice>
  {
    @Override
    public int compare(Slice o1, Slice o2)
    {
      return WritableComparator.compareBytes(o1.buffer, o1.offset, o1.length, o2.buffer, o2.offset, o2.length);
    }
  }

  public static class BucketFileMeta
  {
    /**
     * Name of file (relative to bucket)
     */
    public String name;
    /**
     * Lower bound sequence key
     */
    public Slice startKey;

    @Override
    public String toString()
    {
      return "BucketFileMeta [name=" + name + ", fromSeq=" + startKey + "]";
    }
  }

  /**
   * Meta data about bucket, persisted in store
   * Flushed on compaction
   */
  public static class BucketMeta
  {
    protected BucketMeta(Comparator<Slice> cmp)
    {
      files = Maps.newTreeMap(cmp);
    }

    @SuppressWarnings("unused")
    private BucketMeta()
    {
      // for serialization only
      files = null;
    }

    protected BucketFileMeta addFile(long bucketKey, Slice startKey)
    {
      BucketFileMeta bfm = new BucketFileMeta();
      bfm.name = Long.toString(bucketKey) + '-' + this.fileSeq++;
      bfm.startKey = startKey;
      files.put(startKey, bfm);
      return bfm;
    }

    int fileSeq;
    long committedWid;
    final TreeMap<Slice, BucketFileMeta> files;
  }

  private static class BucketReader
  {
    BucketMeta bucketMeta;
    final HashMap<String, HDSFileReader> readers = Maps.newHashMap();
  }

}
