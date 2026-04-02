# Add speed modifier to an entity (similar to Java applySpeedModifier)
# This adds a temporary speed bonus modifier

# Get the entity's speed attribute
attribute @s minecraft:generic.movement_speed modifier add my_addon:jukebox_speed "0.10" multiply_total
# Note: In a real implementation, we'd need to use the proper UUID and check for existing modifiers
# This is a simplified version for demonstration