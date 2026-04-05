# Get nearby whitelist entities and store them in a temporary list
# This mimics the Java getNearbyWhitelistEntities method

# Clear any existing list
scoreboard players set @s jukebox_list_index 0

# Get all entities in range and check if they're in whitelist
execute as @s at @s run execute as @e[distance=..20] run function my_addon:jukebox_check_whitelist