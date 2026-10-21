/**
 * @name Network string read without an explicit maximum
 * @description Network strings should use an explicit size bound before allocation or decoding.
 * @kind problem
 * @problem.severity warning
 * @precision high
 * @id mcenvision/neoforge/unbounded-network-string
 * @tags security
 *       external/cwe/cwe-400
 */

import java

from MethodCall read
where
  read.getMethod().getName() = "readUtf" and
  read.getNumArgument() = 0 and
  (
    read.getMethod().getDeclaringType().getQualifiedName().matches("%FriendlyByteBuf%") or
    read.getMethod().getDeclaringType().getQualifiedName().matches("%RegistryFriendlyByteBuf%")
  )
select read, "Use the bounded readUtf overload with a protocol specific maximum length."
