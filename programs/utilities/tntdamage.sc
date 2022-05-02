__config()->{
	'scope' -> 'global',
	'stay_loaded' -> false
};

global_enabled = false;
global_damage_map={};
__on_explosion_outcome(pos, power, source, causer, mode, fire, blocks, entities) -> (

	for(entities,
		y=floor(pos(_):1);
		if(global_damage_map:y==null, global_damage_map:y=1);
		global_damage_map:y +=1;
	);
);

__command()->null;

start() -> global_enabled = true;
stop() -> global_enabled = false;
display() -> (
	for(keys(global_damage,map),
		print(player('all'),str('Entities damaged on y%s :%s',_,global_damage_map:_));
	);
); 