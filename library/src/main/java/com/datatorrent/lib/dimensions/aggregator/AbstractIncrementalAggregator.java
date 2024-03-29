/**
 * Copyright (c) 2015 DataTorrent, Inc.
 * All rights reserved.
 */
package com.datatorrent.lib.dimensions.aggregator;

import com.google.common.base.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datatorrent.lib.appdata.gpo.GPOMutable;
import com.datatorrent.lib.appdata.gpo.GPOUtils;
import com.datatorrent.lib.appdata.schemas.TimeBucket;
import com.datatorrent.lib.dimensions.AbstractDimensionsComputationFlexibleSingleSchema.DimensionsConversionContext;
import com.datatorrent.lib.dimensions.DimensionsEvent.Aggregate;
import com.datatorrent.lib.dimensions.DimensionsEvent.EventKey;
import com.datatorrent.lib.dimensions.DimensionsEvent.InputEvent;

/**
 * This is a base class for {@link IncrementalAggregator}s which provides common functionality.
 */
public abstract class AbstractIncrementalAggregator implements IncrementalAggregator
{
  private static final long serialVersionUID = 201506211153L;

  /**
   * The conversion context for this aggregator.
   */
  protected DimensionsConversionContext context;

  public AbstractIncrementalAggregator()
  {
  }

  @Override
  public void setDimensionsConversionContext(DimensionsConversionContext context)
  {
    this.context = Preconditions.checkNotNull(context);
  }

  @Override
  public Aggregate getGroup(InputEvent src, int aggregatorIndex)
  {
    src.used = true;
    Aggregate aggregate = createAggregate(src,
                                          context,
                                          aggregatorIndex);
    return aggregate;
  }

  @Override
  public int computeHashCode(InputEvent inputEvent)
  {
    int hashCode = GPOUtils.indirectHashcode(inputEvent.getKeys(), context.indexSubsetKeys);

    if(this.context.inputTimestampIndex != -1
       && this.context.outputTimebucketIndex != -1) {
      hashCode ^= inputEvent.getKeys().getFieldsLong()[this.context.inputTimestampIndex];
      hashCode ^= this.context.dd.getTimeBucket().roundDown(inputEvent.getKeys().getFieldsLong()[this.context.inputTimestampIndex]);
    }

    return hashCode;
  }

  @Override
  public boolean equals(InputEvent inputEvent1, InputEvent inputEvent2)
  {
    long timestamp1 = 0;
    long timestamp2 = 0;

    if(context.inputTimestampIndex != -1) {
      timestamp1 = inputEvent1.getKeys().getFieldsLong()[context.inputTimestampIndex];
      inputEvent1.getKeys().getFieldsLong()[context.inputTimestampIndex] =
      context.dd.getTimeBucket().roundDown(timestamp1);

      timestamp2 = inputEvent2.getKeys().getFieldsLong()[context.inputTimestampIndex];
      inputEvent2.getKeys().getFieldsLong()[context.inputTimestampIndex] =
      context.dd.getTimeBucket().roundDown(timestamp2);
    }

    boolean equals = GPOUtils.subsetEquals(inputEvent2.getKeys(),
                                           inputEvent1.getKeys(),
                                           context.indexSubsetKeys);

    if(context.inputTimestampIndex != -1) {
      inputEvent1.getKeys().getFieldsLong()[context.inputTimestampIndex] = timestamp1;
      inputEvent2.getKeys().getFieldsLong()[context.inputTimestampIndex] = timestamp2;
    }

    return equals;
  }

  /**
   * Creates an {@link Aggregate} from the given {@link InputEvent}.
   * @param inputEvent The {@link InputEvent} to unpack into an {@link Aggregate}.
   * @param context The conversion context required to transform the {@link InputEvent} into
   * the correct {@link Aggregate}.
   * @param aggregatorIndex The aggregatorIndex assigned to this {@link Aggregate}.
   * @return The converted {@link Aggregate}.
   */
  public static Aggregate createAggregate(InputEvent inputEvent,
                                          DimensionsConversionContext context,
                                          int aggregatorIndex)
  {
    GPOMutable aggregates = new GPOMutable(context.aggregateDescriptor);
    EventKey eventKey = createEventKey(inputEvent,
                                       context,
                                       aggregatorIndex);

    Aggregate aggregate = new Aggregate(eventKey,
                                        aggregates);
    aggregate.setAggregatorIndex(aggregatorIndex);

    return aggregate;
  }

  /**
   * Creates an {@link EventKey} from the given {@link InputEvent}.
   * @param inputEvent The {@link InputEvent} to extract an {@link EventKey} from.
   * @param context The conversion context required to extract the {@link EventKey} from
   * the given {@link InputEvent}.
   * @param aggregatorIndex The aggregatorIndex to assign to this {@link InputEvent}.
   * @return The {@link EventKey} extracted from the given {@link InputEvent}.
   */
  public static EventKey createEventKey(InputEvent inputEvent,
                                        DimensionsConversionContext context,
                                        int aggregatorIndex)
  {
    GPOMutable keys = new GPOMutable(context.keyDescriptor);
    GPOUtils.indirectCopy(keys, inputEvent.getKeys(), context.indexSubsetKeys);

    if(context.outputTimebucketIndex >= 0) {
      TimeBucket timeBucket = context.dd.getTimeBucket();

      keys.getFieldsInteger()[context.outputTimebucketIndex] = timeBucket.ordinal();
      keys.getFieldsLong()[context.outputTimestampIndex] =
      timeBucket.roundDown(inputEvent.getKeys().getFieldsLong()[context.inputTimestampIndex]);
    }

    EventKey eventKey = new EventKey(context.schemaID,
                                     context.dimensionsDescriptorID,
                                     context.aggregatorID,
                                     keys);

    return eventKey;
  }

  private static final Logger LOG = LoggerFactory.getLogger(AbstractIncrementalAggregator.class);
}
