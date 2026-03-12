package gg.modl.backend.ai.data;

public interface DefaultPrompts {

    String MINECRAFT = """
        Role: Expert Minecraft Moderator.
        Task: Assess if the reported player ({{REPORTED_PLAYER}}) violated rules in the chat log.

        PUNISHMENT RULES (ID: Description):
        {{PUNISHMENT_TYPES}}

        SEVERITY LEVELS:
        - low: Clearly violating the rule in a demonstratable way, yet not withstanding any aggravating factors or repeated violations
        - regular: Fragrantly violating the rule, withstanding some aggravating factors or repeated violations
        - severe: Multiple repeated violations, complete disregard of the rule, excessive amount of aggravating factors

        CONSTRAINTS:
        1. ONLY evaluate {{REPORTED_PLAYER}}'s behavior. Use other players' messages strictly for context.
        2. Match violations directly to the provided PUNISHMENT RULES.
        3. If no rules are clearly violated, determine that no action is needed.

        CHAT LOG:
        {{CHAT_LOG}}
        """;

    String JSON_FORMAT = """
        {
          "type": "object",
          "properties": {
            "analysis": {
              "type": "string",
              "description": "A brief explanation of what rule violations, if any, were found in the chat."
            },
            "suggestedAction": {
              "type": "object",
              "nullable": true,
              "description": "The recommended action to take, or null if no action is necessary.",
              "properties": {
                "punishmentTypeId": {
                  "type": "string",
                  "description": "The unique identifier for the type of punishment."
                },
                "severity": {
                  "type": "string",
                  "enum": [
                    "low",
                    "regular",
                    "severe"
                  ],
                  "description": "The intensity level of the action."
                }
              },
              "required": [
                "punishmentTypeId",
                "severity"
              ]
            }
          },
          "required": [
            "analysis",
            "suggestedAction"
          ]
        }""";
}
