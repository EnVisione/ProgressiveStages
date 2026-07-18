ProgressiveStages.condition("pack:ready_for_stage", player => player.level >= 10, {
  title: "Ready for stage",
  description: "True when the player has at least ten experience levels",
  arguments: [],
  scopes: ["player"],
  eventInterests: ["level"]
})

ProgressiveStages.condition("pack:trial_active", player => player.persistentData.trialActive === true, {
  title: "Trial active",
  description: "Activates the temporary tool allow",
  arguments: [],
  scopes: ["player"],
  eventInterests: ["kubejs"]
})

ProgressiveStages.action("pack:announce_stage", context => {
  context.player.tell(context.arguments.message)
  return { success: true, code: "ok", explanation: "Message sent" }
}, {
  title: "Announce stage",
  description: "Sends a configured message",
  arguments: [{ name: "message", type: "string", required: true }]
})
