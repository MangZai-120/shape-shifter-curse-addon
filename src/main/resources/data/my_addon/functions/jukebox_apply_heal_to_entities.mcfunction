# Apply heal to entities in the whitelist list
# This is a simplified version - in practice we'd iterate through the list
# For now, we'll heal all nearby non-monster entities (similar to Java logic when whitelist empty)

# Get player position and heal nearby entities
execute as @s at @s run execute as @e[distance=..20,type=!minecraft:zombie,type=!minecraft:skeleton,type=!minecraft:creeper] run effect give @s minecraft:healing 1 0 true