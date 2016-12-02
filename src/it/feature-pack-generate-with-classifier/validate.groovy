/*
 * Copyright 2013-2016 smartics, Kronseder & Reiner GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
def base = "${targetDir}/net/sf/json-lib/main"

def modulesFile = new File(basedir, base + '/module.xml')
assert modulesFile.exists()
def module = new XmlSlurper().parse(modulesFile)

def name = module.@name.text()
assert 'net.sf.json-lib' == name

def artifacts = module.resources.artifact;
assert 1 == artifacts.size()
assert '${net.sf.json-lib:json-lib::jdk15}' == artifacts[0].@name.text()


/*
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.1" name="net.sf.json-lib">
  <resources>
    <artifact name="${net.sf.json-lib:json-lib::jdk15}" />
  </resources>
</module>
*/
