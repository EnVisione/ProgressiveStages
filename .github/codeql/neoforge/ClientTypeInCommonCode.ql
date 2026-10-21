/**
 * @name Minecraft client type referenced outside a client package
 * @description Referencing client classes from common code can crash a dedicated server.
 * @kind problem
 * @problem.severity error
 * @precision high
 * @id mcenvision/neoforge/client-type-in-common-code
 * @tags reliability
 *       maintainability
 */

import java

from TypeAccess access, RefType type
where
  access.getType() = type and
  type.getQualifiedName().matches("net.minecraft.client.%") and
  not access.getFile().getRelativePath().matches("%/client/%") and
  not access.getFile().getRelativePath().matches("%/datagen/%") and
  not access.getFile().getRelativePath().matches("%/test/%")
select access, "Client type " + type.getQualifiedName() + " is referenced outside a client package."
