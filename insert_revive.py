import re

path = r"C:\Users\user\Desktop\mods\AAS\src\main\java\com\example\aas\client\ClientEvents.java"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Insert after the line containing 'private static boolean revivingHeld = false;'
new_lines = """
    // Client-side revive cooldown timer (ticks). Counts up while holding key.
    // When it reaches the required duration, we signal the server to finish the revive.
    private static int clientReviveStartTick = -1;
    // Required ticks for revive: 8 seconds for normal, 3 seconds for medic (20 ticks/sec)
    private static final int REVIVE_TICKS_NORMAL = AASConfig.REVIVE_HOLD_SECONDS.get() * 20;
    private static final int REVIVE_TICKS_MEDIC = AASConfig.MEDIC_REVIVE_HOLD_SECONDS.get() * 20;

"""

# Insert after the line containing 'private static boolean revivingHeld = false;'
pattern = r"(    private static boolean revivingHeld = false;)"
match = re.search(pattern, content)
if match:
    # Insert after the matched line
    insert_pos = match.end()
    new_content = content[:insert_pos] + new_lines + content[insert_pos:]
    with open(path, "w", encoding="utf-8") as f:
        f.write(new_content)
    print("Lines inserted successfully")
else:
    print("Could not find target line")
    # Print surrounding lines for debugging
    lines = content.split('\n')
    for i, line in enumerate(lines):
        if 'revivingHeld' in line:
            print(f"Line {i}: {repr(line)}")