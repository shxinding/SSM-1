#!/usr/bin/env bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# === EXAMPLE ===
#./target/start-ssm.sh -D dfs.ssm.namenode.rpcserver=hdfs://localhost:9000
#./target/start-ssm.sh -D dfs.ssm.namenode.rpcserver=hdfs://localhost:9000 -D dfs.ssm.default.db.url=jdbc:sqlite:/root/ssm-test.db

function ssm_usage
{
  echo "Usage: start-ssm.sh"
}

function ssm_exit_with_usage
{
  local exitcode=$1
  if [[ -z $exitcode ]]; then
    exitcode=1
  fi
  ssm_usage
  exit $exitcode
}

DEBUG=
args=
for var in $*; do
  if [ X"$var" = X"-debug" ]; then
    DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,address=8008,server=y,suspend=y"
  else
    args="$args $var"
  fi
done

script="${BASH_SOURCE-$0}"
bin=$(cd -P -- "$(dirname -- "${script}")" >/dev/null && pwd -P)

SSMPOC_HOME=${bin}/../target
CLASS_PATH=$SSMPOC_HOME/hadoop-ssm-project-3.0.0-alpha3-SNAPSHOT.jar:$SSMPOC_HOME/lib/*:.
java $DEBUG -classpath "$CLASS_PATH" org.apache.hadoop.ssm.SSMServer $*

