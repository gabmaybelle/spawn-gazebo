# Runs once per player, on their first join (advancement spawn_gazebo:starter_kit). SpawnGazebo.java
# sets storage spawn_gazebo:spawn {chest:1b} when it builds the gazebo with its chest. The first player
# gets the chest; everyone after them is handed the same kit, unless Lootr is giving each player their
# own copy of the chest ({lootr:1b}). Without the chest, or in a world from before the mod, nothing is given.
execute if data storage spawn_gazebo:spawn {chest:1b} unless data storage spawn_gazebo:spawn {lootr:1b} if data storage spawn_gazebo:spawn {claimed:1b} run function spawn_gazebo:starter_kit/give
execute if data storage spawn_gazebo:spawn {chest:1b} run data merge storage spawn_gazebo:spawn {claimed:1b}
