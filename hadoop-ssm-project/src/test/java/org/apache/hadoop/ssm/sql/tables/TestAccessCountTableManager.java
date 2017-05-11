/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.ssm.sql.tables;

import org.apache.hadoop.hdfs.protocol.FileAccessEvent;
import org.apache.hadoop.ssm.sql.DBAdapter;
import org.apache.hadoop.ssm.sql.DBTest;
import org.apache.hadoop.ssm.utils.Constants;
import org.apache.hadoop.ssm.utils.TimeGranularity;
import org.dbunit.Assertion;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.SortedTable;
import org.dbunit.dataset.xml.XmlDataSet;
import org.junit.Assert;
import org.junit.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class TestAccessCountTableManager extends DBTest {

  @Test
  public void testAccessCountTableManager() {
    DBAdapter adapter = mock(DBAdapter.class);
    AccessCountTableManager manager = new AccessCountTableManager(adapter);
    Long firstDayEnd = 24 * 60 * 60 * 1000L;
    AccessCountTable accessCountTable =
        new AccessCountTable(firstDayEnd - 5 * 1000, firstDayEnd, TimeGranularity.SECOND);
    manager.addTable(accessCountTable);

    Map<TimeGranularity, AccessCountTableDeque> map = manager.getTableDeques();
    AccessCountTableDeque second = map.get(TimeGranularity.SECOND);
    Assert.assertTrue(second.size() == 1);
    Assert.assertEquals(second.peek(), accessCountTable);

    AccessCountTableDeque minute = map.get(TimeGranularity.MINUTE);
    AccessCountTable minuteTable =
        new AccessCountTable(firstDayEnd - 60 * 1000, firstDayEnd, TimeGranularity.MINUTE);
    Assert.assertTrue(minute.size() == 1);
    Assert.assertEquals(minute.peek(), minuteTable);

    AccessCountTableDeque hour = map.get(TimeGranularity.HOUR);
    AccessCountTable hourTable =
        new AccessCountTable(firstDayEnd - 60 * 60 * 1000, firstDayEnd, TimeGranularity.HOUR);
    Assert.assertTrue(hour.size() == 1);
    Assert.assertEquals(hour.peek(), hourTable);

    AccessCountTableDeque day = map.get(TimeGranularity.DAY);
    AccessCountTable dayTable =
        new AccessCountTable(firstDayEnd - 24 * 60 * 60 * 1000, firstDayEnd, TimeGranularity.DAY);
    Assert.assertTrue(day.size() == 1);
    Assert.assertEquals(day.peek(), dayTable);
  }

  private void createTables(IDatabaseConnection connection) throws Exception {
    Statement statement = connection.getConnection().createStatement();
    statement.execute(AccessCountTable.createTableSQL("expect1"));
    String sql =
        "CREATE TABLE `files` (" + "`path` varchar(4096) NOT NULL," + "`fid` bigint(20) NOT NULL )";
    statement.execute(sql);
    statement.close();
  }

  @Test
  public void testAddAccessCountInfo() throws Exception {
    createTables(databaseTester.getConnection());
    IDataSet dataSet = new XmlDataSet(getClass().getClassLoader().getResourceAsStream("files.xml"));
    databaseTester.setDataSet(dataSet);
    databaseTester.onSetup();

    DBAdapter adapter = new DBAdapter(databaseTester.getConnection().getConnection());
    AccessCountTableManager manager = new AccessCountTableManager(adapter);
    List<FileAccessEvent> accessEvents = new ArrayList<>();
    accessEvents.add(new FileAccessEvent("file1", 0));
    accessEvents.add(new FileAccessEvent("file2", 1));
    accessEvents.add(new FileAccessEvent("file2", 2));
    accessEvents.add(new FileAccessEvent("file3", 2));
    accessEvents.add(new FileAccessEvent("file3", 3));
    accessEvents.add(new FileAccessEvent("file3", 4));

    accessEvents.add(new FileAccessEvent("file3", 5000));

    manager.onAccessEventsArrived(accessEvents);
    AccessCountTable accessCountTable = new AccessCountTable(0L, 5000L);
    ITable actual = databaseTester.getConnection().createTable(accessCountTable.getTableName());
    ITable expect = databaseTester.getDataSet().getTable("expect1");
    SortedTable sortedActual = new SortedTable(actual, new String[] {"file_id"});
    sortedActual.setUseComparable(true);
    Assertion.assertEquals(expect, sortedActual);
  }

  @Test
  public void testGetTables() throws SQLException {
    DBAdapter adapter = mock(DBAdapter.class);
    TableEvictor tableEvictor = new CountEvictor(20);
    Map<TimeGranularity, AccessCountTableDeque> map = new HashMap<>();
    AccessCountTableDeque dayDeque = new AccessCountTableDeque(tableEvictor);
    AccessCountTable firstDay = new AccessCountTable(0L, Constants.ONE_DAY_IN_MILLIS);
    dayDeque.add(firstDay);
    map.put(TimeGranularity.DAY, dayDeque);

    AccessCountTableDeque hourDeque = new AccessCountTableDeque(tableEvictor);
    AccessCountTable firstHour =
        new AccessCountTable(23 * Constants.ONE_HOUR_IN_MILLIS, 24 * Constants.ONE_HOUR_IN_MILLIS);
    AccessCountTable secondHour =
        new AccessCountTable(24 * Constants.ONE_HOUR_IN_MILLIS, 25 * Constants.ONE_HOUR_IN_MILLIS);
    hourDeque.add(firstHour);
    hourDeque.add(secondHour);
    map.put(TimeGranularity.HOUR, hourDeque);

    AccessCountTableDeque minuteDeque = new AccessCountTableDeque(tableEvictor);
    Integer numMins = 25 * 60;
    AccessCountTable firstMin =
        new AccessCountTable(
            (numMins - 1) * Constants.ONE_MINUTE_IN_MILLIS,
            numMins * Constants.ONE_MINUTE_IN_MILLIS);
    AccessCountTable secondMin =
        new AccessCountTable(
            numMins * Constants.ONE_MINUTE_IN_MILLIS,
            (numMins + 1) * Constants.ONE_MINUTE_IN_MILLIS);
    minuteDeque.add(firstMin);
    minuteDeque.add(secondMin);
    map.put(TimeGranularity.MINUTE, minuteDeque);

    AccessCountTableDeque secondDeque = new AccessCountTableDeque(tableEvictor);
    Integer numSeconds = (25 * 60 + 1) * 60;
    AccessCountTable firstFiveSeconds =
        new AccessCountTable(
            (numSeconds - 5) * Constants.ONE_SECOND_IN_MILLIS,
            numSeconds * Constants.ONE_SECOND_IN_MILLIS);
    AccessCountTable secondFiveSeconds =
        new AccessCountTable(
            numSeconds * Constants.ONE_SECOND_IN_MILLIS,
            (numSeconds + 5) * Constants.ONE_SECOND_IN_MILLIS);
    secondDeque.add(firstFiveSeconds);
    secondDeque.add(secondFiveSeconds);
    map.put(TimeGranularity.SECOND, secondDeque);

    List<AccessCountTable> firstResult =
        AccessCountTableManager.getTables(
            map, adapter, (numSeconds + 5) * Constants.ONE_SECOND_IN_MILLIS);
    Assert.assertTrue(firstResult.size() == 4);
    Assert.assertEquals(firstResult.get(0), firstDay);
    Assert.assertEquals(firstResult.get(1), secondHour);
    Assert.assertEquals(firstResult.get(2), secondMin);
    Assert.assertEquals(firstResult.get(3), secondFiveSeconds);

    List<AccessCountTable> secondResult =
        AccessCountTableManager.getTables(
            map, adapter, numSeconds * Constants.ONE_SECOND_IN_MILLIS);
    Assert.assertTrue(secondResult.size() == 4);

    AccessCountTable expectDay =
        new AccessCountTable(5 * Constants.ONE_SECOND_IN_MILLIS, Constants.ONE_DAY_IN_MILLIS);
    Assert.assertEquals(expectDay, secondResult.get(0));

    List<AccessCountTable> thirdResult =
        AccessCountTableManager.getTables(
            map, adapter, secondFiveSeconds.getEndTime() - 23 * Constants.ONE_HOUR_IN_MILLIS);
    Assert.assertTrue(thirdResult.size() == 4);
    Assert.assertEquals(thirdResult.get(0), firstHour);

    List<AccessCountTable> fourthResult =
        AccessCountTableManager.getTables(
            map, adapter, secondFiveSeconds.getEndTime() - 24 * Constants.ONE_HOUR_IN_MILLIS);
    Assert.assertTrue(fourthResult.size() == 3);
    Assert.assertEquals(fourthResult.get(0), secondHour);
  }
}
