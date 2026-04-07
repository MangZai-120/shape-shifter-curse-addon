# Jukebox effects processing function
# This function handles the jukebox effects based on mode
# It will be called from the tick power every 20 ticks (1 second)

# Get the player executing this function
execute as @s at @s run function my_addon:jukebox_check_mode

# Function to check mode and apply effects