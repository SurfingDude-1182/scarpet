// Tweak Stuff
    global_request_time = 60;   //Time between 2 requests, if requests is turned on (in seconds)
    global_allow_request = true;   //If enabled, does not send unlocking request to player
    global_allow_only_owner = false; //Only grave owners are allowed to open their graves


//Boring Stuff*****************************************************************************************************************************************
__config()->{
    'stay_loaded' -> true,
    'scope' -> 'global',
    'requires' -> {
        'carpet' -> '>=1.4.57'
    }
};

__command() -> null;

gdata_file = read_file('gdata','JSON');
if(gdata_file == null,
    global_gdata = {},
    //else when file already exists
    global_gdata = gdata_file;
);


//Player Events

__on_player_right_clicks_block(player, item_tuple, hand, block, face, hitvec)->(
	
    if(block!='player_head' || hand!='mainhand',return());
    owner = block_data(pos(block)) ~ query(player,'name') != null;
    //owner= !owner; //If you dont have friends, use for testing purpose..
    if(owner, _owner_clicked(player,block));
    if(!owner && !global_allow_only_owner, _not_owner_clicked(player,block));
);

__on_player_breaks_block(player, block)->(

    if(block != 'player_head', return());
    nbt = nbt(block_data(block));
    schedule(0,'_place_head',player,nbt);
    schedule(0,'_delete_entity',player,pos(block),2,2,2);
    
);

__on_explosion_outcome(pos, power, source, causer, mode, fire, blocks, entities)->(
    for(blocks,
        if(_ == 'player_head',
            posG = pos(_);
            posG:0 = floor(posG:0);
            posG:1 = floor(posG:1);
            posG:2 = floor(posG:2);
            schedule(50,'_replace',posG)
            ));
);

__on_player_dies(player)->(

    if(!inventory_has_items(player), return());

    //To do: Nearest air block
    if(air(pos(player)),
        gravepos = pos(player),
        //else
        gravepos = pos_offset(pos(player),'up');
    );

    gravepos:0 = floor(gravepos:0);
    gravepos:2 = floor(gravepos:2);
    if(pos(player):1<0, gravepos:1 = 1); 

    print(player,format(str('nb A grave has been formed at %d,%d,%d',gravepos)));

    _replacer(gravepos,player);

    item_count=0;
    put(global_gdata:str(gravepos),{},null);

    c_for(i=0,i<inventory_size(player),i+=1,

        if(inventory_get(player,i)!=null,

            slotItem= inventory_get(player,i);
            put(global_gdata:str(gravepos):str(i),slotItem);
            item_count+=1;
            _saveGData();
        
        );
    
    );

    put(global_gdata:str(gravepos):'name',query(player,'name'),null);
    put(global_gdata:str(gravepos):'xp_level',query(player,'xp_level'),null);
    put(global_gdata:str(gravepos):'xp_progress',query(player,'xp_progress'),null);
    put(global_gdata:str(gravepos):'locked',true,null);
    put(global_gdata:str(gravepos):'item_count',item_count,null);
    put(global_gdata:str(gravepos):'canRequest',true,null);
    _saveGData();

    run(str('clear %s', player));
    modify(player,'xp_level',0);
    modify(player,'xp_progress',0);
);



//Grave Click Functions

_not_owner_clicked(player,block) -> (

    owner= global_gdata:str(pos(block)):'name';
    if(player('all')~owner==null || !global_allow_request , global_gdata:str(pos(block)):'locked' = false);
    
    //locked
    if(global_gdata:str(pos(block)):'locked' && global_gdata:str(pos(block)):'canRequest' && global_allow_request,
    
        global_gdata:str(pos(block)):'canRequest'=false;
        schedule( global_request_time*20 , '_request' , global_gdata,block);
        gdata=global_gdata:str(pos(block));

        requestScreen=create_screen(owner,'generic_3x3',str('%s\'s Request',player), _(screen,player,action,data,outer(gdata)) -> (
            if(action=='pickup' && data:'slot'==4,
                gdata:'locked'=false;
                close_screen(screen);
            );
        ));
        if(screen_property(requestScreen,'open'),

            item='lime_concrete{display:{Name:\'{"text":"[Accept]","color":"green","italic":"false","bold":"true"}\'}}';
            inventory_set(requestScreen,4,1,item);
        
        ),

        global_gdata:str(pos(block)):'locked' && !global_gdata:str(pos(block)):'canRequest',
            print(player,format('br Permission denied. Try asking after some time.'));    
    );


    //not locked
    if(!global_gdata:str(pos(block)):'locked',

        gdata=global_gdata:str(pos(block));
        item_count=gdata:'item_count';
        
        if(gdata:'name'==null,
            run(str('setblock %d %d %d minecraft:air',pos(block):0, pos(block):1, pos(block):2));
            return();
        );


        deathCrate= create_screen(player,'generic_9x5',str('%s\'s Death Crate',gdata:'name'),_(screen,player,action,data,outer(gdata),outer(block)) ->(

            if(action=='pickup' || action== 'swap' || action=='quick_move' || action== 'throw' || action== 'pickup_all',
                _delete_data_slot(gdata,data:'slot');
                datafound=false;

                c_for(i=0,i<41,i+=1,if(gdata:str(i)!=null, datafound=true));

                if(datafound==false,
                    _delete_data(pos(block));
                    run(str('setblock %d %d %d minecraft:air',pos(block):0, pos(block):1, pos(block):2)); //for some reason /set doesnt seem to work. I might be doing bonk stuff somewhere
                );
            );
        ));


        if(screen_property(deathCrate,'open'),
            c_for(i=0,i<41,i+=1,
                item=gdata:str(i);
                inventory_set(deathCrate,i,item:1,item:0,item:2);
            );       
        );
        if(!gdata:'opened',modify(player,'add_xp',global_gdata:str(pos(block)):'xp'));
        gdata:'opened'=true;
    );
);

_owner_clicked(player,block) -> (
    gdata=global_gdata:str(pos(block));
    itemCount=0;
    c_for(i=0,i<inventory_size(player),i+=1,
        item=global_gdata:str(pos(block)):str(i);
        if(item!=null && inventory_get(player,i)==null,
            inventory_set(player,i,item:1,item:0,item:2);
            _delete_data_slot(gdata,str(i)),
         // when something is in slot
            item!=null && inventory_get(player,i)!=null,
            itemCount+=1;
        );
    );

    if(itemCount == 0,
        modify(player,'xp_level',gdata:'xp_level');
        modify(player,'xp_progress',gdata:'xp_progress');
        set(pos(block),'air');
        _delete_data(pos(block));
        return();          
    );

    deathCrate= create_screen(player,'generic_9x5',str('%s\'s Death Crate',gdata:'name'),_(screen,player,action,data,outer(gdata),outer(block)) ->(

            if(action=='pickup' || action== 'swap' || action=='quick_move' || action== 'throw' || action== 'pickup_all',
                _delete_data_slot(gdata,data:'slot');
                datafound=false;
            c_for(i=0,i<41,i+=1,if(gdata:str(i)!=null, datafound=true));

            if(datafound==false,
            _delete_data(pos(block));
            modify(player,'xp_level',gdata:'xp_level');
            modify(player,'xp_progress',gdata:'xp_progress');
            run(str('setblock %d %d %d minecraft:air',pos(block):0, pos(block):1, pos(block):2));
            );
            );

        ));

    if(screen_property(deathCrate,'open'),

            c_for(i=0,i<41,i+=1,
                item=gdata:str(i);
                inventory_set(deathCrate,i,item:1,item:0,item:2);
            );      
        );
);




//Utility Functions

//Debug will replace all player heads that were destroyed for one reason or other. It will also print all replaced grave positions to player
debug() -> (
    locationStrings=keys(global_gdata);
    coordsList=[];
    for(locationStrings,
        s= replace(str(_),'\\[','');
        s=replace(s,'\\]','');
        s=replace(s,' ','');
        coords= split('\\,',s);
        coordsList:_i= coords;
    );

    print(player(),coordsList);

    for(coordsList,
        x=number(_:0);
        y=number(_:1);
        z=number(_:2);
        pos=[x,y,z];
        owner= global_gdata:str(pos):'name';
        run(str('setblock %s %s %s minecraft:air',x,y,z));
        run(str('setblock %s %s %s minecraft:player_head{ExtraType : "%s"}',x,y,z,owner));
    );

    null;
);

_request(global_gdata,block) -> (

    global_gdata:str(pos(block)):'canRequest'=true;

);

_place_head(player,nbt) -> (

    if(player~'gamemode'!='survival', return());
    run(str('setblock %s %s %s minecraft:player_head{ExtraType:"%s"}',nbt:'x',nbt:'y',nbt:'z',nbt:'SkullOwner':'Name'));

);

_delete_entity(player,coords,dx,dy,dz) -> (

    if(query(player,'gamemode')=='creative',return());
    selector_item = str('@e[type=item,x=%d,y=%d,z=%d,dx=%d,dy=%d,dz=%d]',coords:0,coords:1,coords:2,dx,dy,dz);
    item = entity_selector(selector_item); 
    selector_xp = str('@e[type=experience_orb,x=%d,y=%d,z=%d,dx=%d,dy=%d,dz=%d]',coords:0,coords:1,coords:2,dx,dy,dz);
    xp = entity_selector(selector_xp);
    if(length(item)!=0, modify(entity_selector(selector_item):0,'remove'));    
    if(length(xp)!=0, modify(entity_selector(selector_xp):0,'remove'));

);

_saveGData() -> (
    delete_file('gdata','JSON');
    write_file('gdata','JSON',global_gdata);
);


_replacer(pos, player) -> (

    pos:0=floor(pos:0);
    pos:1=floor(pos:1);
    pos:2=floor(pos:2);
    set(pos,'air');
    run(str('setblock %s %s %s minecraft:player_head{ExtraType:"%s"}',pos:0,pos:1,pos:2,player));

);

_replace(pos) ->(

    set(pos,'air');
    run(str('setblock %s %s %s minecraft:player_head{ExtraType:"%s"}',pos:0,pos:1,pos:2,global_gdata:str(pos):'name'));

);


_delete_data(pos) -> (

    delete(global_gdata,str(pos));
    delete_file('gdata','JSON');
    _saveGData();

);

_delete_data_slot(data,slotNum) -> (

    delete(data,str(slotNum));
    delete_file('gdata','JSON');
    _saveGData();

);
