ServerEvents.recipes(event => {
    event.recipes.terra_plate_extension.terra_plate({
        ingredients: [{ item: 'minecraft:iron_ingot' }],
        mana: 0,
        output: { item: 'minecraft:diamond' }
    });
});