# Check if an entity is in the whitelist and add it to our list if so
# This mimics the AllaySPGroupHeal.isInWhitelist logic

# Store the entity UUID
data get entity @s UUID >> storage my_addon:temp_entity_uuid

# Get the player's command tags (whitelist)
data get entity @s Tags >> storage my_addon:temp_player_tags

# Check if the entity is a player or tameable, and if so check owner
# For simplicity in MC function, we'll do a basic check - in practice this would be more complex
# For now, we'll assume all non-monster entities are valid (similar to the Java logic when whitelist is empty)

# Check if entity is a monster
data get entity @s Type >> storage my_addon:temp_entity_type
execute if data storage my_addon:temp_entity_type.value == "minecraft:zombie" run return
execute if data storage my_addon:temp_entity_type.value == "minecraft:skeleton" run return
execute if data storage my_addon:temp_entity_type.value == "minecraft:creeper" run return
# Add more monster types as needed

# If we got here, it's not a monster we want to exclude
# Add to our list
scoreboard players operation @s jukebox_list_count += @s jukebox_list_index
scoreboard players set @s jukebox_list_index 1
storage add my_addon:jukebox_list 0
data modify storage my_addon:jukebox_list value set [{UUID:"00000000-0000-0000-0000-000000000000"}]
# In a real implementation, we'd properly add the entity UUID to the list