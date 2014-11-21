/*
 *  Copyright 2014 TWO SIGMA OPEN SOURCE, LLC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
(function() {
  "use strict";
  window.bkInit.getEvaluatorUrlMap = function() {
    return {
      "IPython": "./plugins/eval/ipythonPlugins/ipython/ipython.js",
      // "Python3": { url : "./plugins/eval/ipythonPlugins/python3/python3.js", bgColor: "#EEBD48", fgColor: "#FFFFFF", borderColor: "", shortName: "Py" },
      // "IRuby": "./plugins/eval/ipythonPlugins/iruby/iruby.js",
      // "Julia": "./plugins/eval/ipythonPlugins/julia/julia.js",
      // "Groovy": "./plugins/eval/groovy/groovy.js",
      // "R": "./plugins/eval/r/r.js",
      "Node": "./plugins/eval/node/node.js"
    };
  };
})();
