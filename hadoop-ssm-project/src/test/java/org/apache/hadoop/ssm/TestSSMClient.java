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
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.ssm.protocol.SSMClient;
import org.apache.hadoop.ssm.rule.RuleInfo;
import org.apache.hadoop.ssm.rule.RuleState;
import org.apache.hadoop.ssm.sql.TestDBUtil;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestSSMClient {

  @Test
  public void test() throws Exception {
    final Configuration conf = new SSMConfiguration();
    final MiniDFSCluster cluster = new MiniDFSCluster.Builder(conf)
        .numDataNodes(4).build();
    // dfs not used , but datanode.ReplicaNotFoundException throws without dfs
    final DistributedFileSystem dfs = cluster.getFileSystem();

    final Collection<URI> namenodes = DFSUtil.getInternalNsRpcUris(conf);
    List<URI> uriList = new ArrayList<>(namenodes);
    conf.set(SSMConfigureKeys.DFS_SSM_NAMENODE_RPCSERVER_KEY,
        uriList.get(0).toString());

    // rpcServer start in SSMServer
    SSMServer server = mock(SSMServer.class);
    Connection conn = TestDBUtil.getUniqueEmptySqliteDBInstance();
    when(server.getDBConnection()).thenReturn(conn);
    Connection c = server.getDBConnection();
    server.createSSM(null, conf);
    SSMClient ssmClient = new SSMClient(conf);

    //test getServiceStatus
    String state = ssmClient.getServiceStatus().getState().name();
    assertTrue("SAFEMODE".equals(state));

    //test getRuleInfo
    RuleInfo ruleInfo = ssmClient.getRuleInfo(5);
    assertEquals(ruleInfo.getState(), RuleState.ACTIVE);

    //test single SSM
    boolean caughtException = false;
    try {
      conf.set(SSMConfigureKeys.DFS_SSM_RPC_ADDRESS_KEY, "localhost:8043");
      SSMServer.createSSM(null, conf);
    } catch (IOException e) {
      assertEquals("java.io.IOException: Another SSMServer is running",
          e.toString());
      caughtException = true;
    }
    assertTrue(caughtException);
  }
}