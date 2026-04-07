# Switch jukebox music based on mode
# Stop both sounds first
execute as @s at @s run playsound minecraft:music_disc_13 master @s ~ ~ ~ 0 0
execute as @s at @s run playsound minecraft:music_disc_cat master @s ~ ~ ~ 0 0

# Get the mode
scoreboard players set @s jukebox_mode_temp 0
data get entity @s Resources["my_addon:form_allay_sp_jukebox_mode"].value 0 >> scoreboard players operation @s jukebox_mode_temp = @s

# Play the appropriate music
execute if score @s jukebox_mode_temp matches 0 run playsound minecraft:music_disc_wait master @s ~ ~ ~ 0.2 1
execute if score @s jukebox_mode_temp matches 1 run playsound minecraft:music_disc_blocks master @s ~ ~ ~ 0.2 1