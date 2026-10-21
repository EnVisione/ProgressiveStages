/**
 * @name Minecraft command literal has no direct permission gate
 * @description Server mutation commands should declare an explicit permission requirement.
 * @kind problem
 * @problem.severity warning
 * @precision medium
 * @id mcenvision/neoforge/command-without-permission
 * @tags security
 *       external/cwe/cwe-862
 */

import java

predicate isLiteral(MethodCall call) {
  call.getMethod().getName() = "literal" and
  call.getMethod().getDeclaringType().hasQualifiedName("net.minecraft.commands", "Commands")
}

from MethodCall literal
where
  isLiteral(literal) and
  not exists(MethodCall requires |
    requires.getMethod().getName() = "requires" and
    requires.getQualifier() = literal
  )
select literal, "This command literal has no direct requires permission gate."
