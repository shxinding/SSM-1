#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

bin=$(dirname "${BASH_SOURCE-$0}")
bin=$(cd "${bin}">/dev/null; pwd)
HOSTNAME=$(hostname)

DAEMON_MOD=
SMART_VARGS=
while [ $# != 0 ]; do
  case "$1" in
    "--config")
      shift
      conf_dir="$1"
      if [[ ! -d "${conf_dir}" ]]; then
        echo "ERROR : ${conf_dir} is not a directory"
        echo ${USAGE}
        exit 1
      else
        export SMART_CONF_DIR="${conf_dir}"
        echo "SMART_CONF_DIR="$SMART_CONF_DIR
      fi
      shift
      ;;
    "--debug")
      JAVA_OPTS+=" -Xdebug -Xrunjdwp:transport=dt_socket,address=8008,server=y,suspend=y"
      shift
      ;;
    "--daemon")
      DAEMON_MOD=1
      shift
      ;;
    *)
      SMART_VARGS+=" $1"
      shift
      ;;
  esac
done

. "${bin}/common.sh"

#---------------------------------------------------------
# Start Smart Servers

SMARTSERVERS=$("${SMART_HOME}/bin/smart.sh" getconf HazelcastMembers 2>/dev/null)

if [ "$?" != "0" ]; then
    echo "${SMARTSERVERS}"
    exit 1
fi

#if [[ -z "${SMARTSERVERS}" ]]; then
#SMARTSERVERS=$HOSTNAME
#fi
echo "SMARTSERVERS=" ${SMARTSERVERS}

#. "${SMART_HOME}/bin/smart.sh" \
# --workers \
# --config "${SMART_CONF_DIR}" \
# --hostnames "${NAMENODES}" \
# --daemon start \
# smartserver ${START_OPTS}

