Use ENDPOINTS.md file for extra context.
1. change landing page registration to new constraints
2. MongoDB migrations:
    - make `totalPlaytime` in all player data in seconds
    - remove `lastSeen` and just use join/leave timestamps
    - 