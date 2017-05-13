/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.ssm;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSUtil;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.ssm.protocol.SSMClient;
import org.apache.hadoop.ssm.rule.RuleState;
import org.apache.hadoop.ssm.sql.TestDBUtil;
import org.apache.hadoop.ssm.sql.Util;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TestSubmitRule {
  private Configuration conf;
  private MiniDFSCluster cluster;
  private SSMServer ssm;

  @Before
  public void setUp() throws Exception {
    conf = new SSMConfiguration();
    cluster = new MiniDFSCluster.Builder(conf)
        .numDataNodes(3).build();

    Collection<URI> namenodes = DFSUtil.getInternalNsRpcUris(conf);
    List<URI> uriList = new ArrayList<>(namenodes);
    conf.set(SSMConfigureKeys.DFS_SSM_NAMENODE_RPCSERVER_KEY,
        uriList.get(0).toString());

    // Set db used
    String dbFile = TestDBUtil.getUniqueEmptySqliteDBFile();
    String dbUrl = Util.SQLITE_URL_PREFIX + dbFile;
    conf.set(SSMConfigureKeys.DFS_SSM_DEFAULT_DB_URL_KEY, dbUrl);

    // rpcServer start in SSMServer
    ssm = SSMServer.createSSM(null, conf);
  }

  @After
  public void cleanUp() {
    if (cluster != null) {
      cluster.shutdown();
    }
  }

  @Test
  public void testSubmitRule() throws Exception {
    String rule = "file: every 1s \n | length > 10 | cachefile";
    SSMClient client = new SSMClient(conf);

    long ruleId = 0l;
    boolean wait = true;
    while (wait) {
      try {
        ruleId = client.submitRule(rule, RuleState.ACTIVE);
        wait = false;
      } catch (IOException e) {
        if (!e.toString().contains("not ready")) {
          throw e;
        }
      }
    }

    for (int i = 0; i < 10; i++) {
      long id = client.submitRule(rule, RuleState.ACTIVE);
      Assert.assertTrue(ruleId + i + 1 == id);
    }

    String badRule = "something else";
    try {
      client.submitRule(badRule, RuleState.ACTIVE);
      Assert.fail("Should have an exception here");
    } catch (IOException e) {
    }

    try {
      client.checkRule(badRule);
      Assert.fail("Should have an exception here");
    } catch (IOException e) {
    }
  }
}
