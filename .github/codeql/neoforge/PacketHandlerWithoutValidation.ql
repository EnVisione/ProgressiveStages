/**
 * @name Network handler has no visible validation
 * @description Packet handlers should validate authority and schedule state changes on the correct thread.
 * @kind problem
 * @problem.severity warning
 * @precision medium
 * @id mcenvision/neoforge/packet-handler-without-validation
 * @tags security
 *       external/cwe/cwe-20
 */

import java

predicate isPacketHandler(Method method) {
  (
    method.getName().matches("%handle%") or
    method.getName().matches("%receive%")
  ) and
  (
    method.getDeclaringType().getPackage().getName().matches("%network%") or
    method.getDeclaringType().getPackage().getName().matches("%packet%")
  )
}

predicate isValidationCall(MethodCall call) {
  call.getMethod().getName() = "enqueueWork" or
  call.getMethod().getName() = "getSender" or
  call.getMethod().getName() = "hasPermission" or
  call.getMethod().getName() = "hasPermissionLevel" or
  call.getMethod().getName() = "isServerSide" or
  call.getMethod().getName() = "isClientSide"
}

from Method method
where
  isPacketHandler(method) and
  not exists(MethodCall call |
    call.getEnclosingCallable() = method and
    isValidationCall(call)
  )
select method, "This network handler has no visible authority, side, or thread validation."
