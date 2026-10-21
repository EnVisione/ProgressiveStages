/**
 * @name Method parameter used directly in filesystem path resolution
 * @description User controlled path segments should be normalized and constrained to an approved root.
 * @kind path-problem
 * @problem.severity warning
 * @precision medium
 * @id mcenvision/neoforge/untrusted-path-resolution
 * @tags security
 *       external/cwe/cwe-22
 */

import java
import semmle.code.java.dataflow.DataFlow

class ParameterSource extends DataFlow::Node {
  ParameterSource() { this.asParameter() instanceof Parameter }
}

class PathResolveSink extends DataFlow::Node {
  PathResolveSink() {
    exists(MethodCall call |
      call.getMethod().getName() = "resolve" and
      call.getMethod().getDeclaringType().hasQualifiedName("java.nio.file", "Path") and
      this.asExpr() = call.getArgument(0)
    )
  }
}

module PathFlowConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) { source instanceof ParameterSource }
  predicate isSink(DataFlow::Node sink) { sink instanceof PathResolveSink }
}

module PathFlow = DataFlow::Global<PathFlowConfig>;
import PathFlow::PathGraph

from PathFlow::PathNode source, PathFlow::PathNode sink
where PathFlow::flowPath(source, sink)
select sink.getNode(), source, sink, "A method parameter reaches filesystem path resolution without a visible root constraint."
