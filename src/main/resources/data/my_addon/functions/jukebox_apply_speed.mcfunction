# Apply speed effect to nearby entities in whitelist
# Get the player position
execute as @s at @s run function my_addon:jukebox_get_nearby_whitelist

# Apply speed modifier to each entity in the list
execute as @s at @s run function my_addon:jukebox_apply_speed_to_entities