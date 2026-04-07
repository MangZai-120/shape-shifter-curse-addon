# Check the mode and apply appropriate effects
# Mode 0 = speed, Mode 1 = heal

# Store mode in a temporary scoreboard for checking
scoreboard players set @s jukebox_mode_temp 0
data get entity @s Resources["my_addon:form_allay_sp_jukebox_mode"].value 0 >> scoreboard players operation @s jukebox_mode_temp = @s

# If mode is 0 (speed), apply speed effect
execute if score @s jukebox_mode_temp matches 0 run function my_addon:jukebox_apply_speed

# If mode is 1 (heal), apply heal effect
execute if score @s jukebox_mode_temp matches 1 run function my_addon:jukebox_apply_heal