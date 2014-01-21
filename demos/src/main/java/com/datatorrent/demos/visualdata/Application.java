/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.demos.visualdata;

import java.net.URI;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;

import com.datatorrent.api.DAG;
import com.datatorrent.api.DAG.Locality;
import com.datatorrent.api.StreamingApplication;
import com.datatorrent.demos.pi.PiCalculateOperator;
import com.datatorrent.lib.io.ConsoleOutputOperator;
import com.datatorrent.lib.io.PubSubWebSocketOutputOperator;
import com.datatorrent.lib.testbench.RandomEventGenerator;

/**
 * Visual data demo.
 */
public class Application implements StreamingApplication {
    private final Locality locality = null;

    @Override
    public void populateDAG(DAG dag, Configuration conf) {
        dag.setAttribute(DAG.APPLICATION_NAME, "VisualDataDemo");
        int maxValue = 30000;

        RandomEventGenerator rand = dag.addOperator("random", new RandomEventGenerator());
        rand.setMinvalue(0);
        rand.setMaxvalue(maxValue);

        ChartValueGenerator chartValue = dag.addOperator("chartValue", new ChartValueGenerator());
        chartValue.setRandomIncrement(5);
        ChartValueGenerator chartValue2 = dag.addOperator("chartValue2", new ChartValueGenerator());
        chartValue2.setRandomIncrement(20);

        PiCalculateOperator calc = dag.addOperator("picalc", new PiCalculateOperator());
        calc.setBase(maxValue * maxValue);
        dag.addStream("rand_calc", rand.integer_data, calc.input).setLocality(locality);

        String gatewayAddress = dag.getValue(DAG.GATEWAY_ADDRESS);
        if (!StringUtils.isEmpty(gatewayAddress)) {
            URI uri = URI.create("ws://" + gatewayAddress + "/pubsub");

            PubSubWebSocketOutputOperator<Object> wsOut = dag.addOperator("wsOut",
                    new PubSubWebSocketOutputOperator<Object>());
            wsOut.setUri(uri);
            wsOut.setTopic("app.visualdata.piValue");
            dag.addStream("ws_pi_data", calc.output, wsOut.input);

            PubSubWebSocketOutputOperator<Object> wsChartOut = dag.addOperator("wsChartOut",
                    new PubSubWebSocketOutputOperator<Object>());
            wsChartOut.setUri(uri);
            wsChartOut.setTopic("app.visualdata.chartValue");
            dag.addStream("ws_chart_data", chartValue.output, wsChartOut.input);

            PubSubWebSocketOutputOperator<Object> wsChartOut2 = dag.addOperator("wsChartOut2",
                    new PubSubWebSocketOutputOperator<Object>());
            wsChartOut.setUri(uri);
            wsChartOut.setTopic("app.visualdata.chartValue2");
            dag.addStream("ws_chart_data2", chartValue2.output, wsChartOut2.input);
        } else {
            ConsoleOutputOperator console = dag.addOperator("console_out", new ConsoleOutputOperator());
            dag.addStream("rand_console", calc.output, console.input).setLocality(locality);

            ConsoleOutputOperator chartConsole = dag.addOperator("chart_out", new ConsoleOutputOperator());
            dag.addStream("chart_console", chartValue.output, chartConsole.input).setLocality(locality);

            ConsoleOutputOperator chartConsole2 = dag.addOperator("chart_out2", new ConsoleOutputOperator());
            dag.addStream("chart_console2", chartValue2.output, chartConsole2.input).setLocality(locality);
        }
    }

}
