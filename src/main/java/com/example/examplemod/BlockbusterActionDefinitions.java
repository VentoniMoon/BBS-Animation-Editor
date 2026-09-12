package com.example.examplemod;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class BlockbusterActionDefinitions
{
    private final Map<Byte, BlockbusterActionDefinition> definitions;

    public BlockbusterActionDefinitions()
    {
        this.definitions = new LinkedHashMap<>();

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 1,
                        "chat"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 2,
                        "swipe"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 3,
                        "drop"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 4,
                        "equip")
                        .addField(
                                "Data",
                                BlockbusterActionDefinition.FieldType.COMPOUND)
                        .addField(
                                "HotbarSlot",
                                BlockbusterActionDefinition.FieldType.BYTE)
                        .addField(
                                "Slot",
                                BlockbusterActionDefinition.FieldType.BYTE));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 5,
                        "shoot_arrow"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 7,
                        "place_block")
                        .addField(
                                "Block",
                                BlockbusterActionDefinition.FieldType.STRING)
                        .addField(
                                "Meta",
                                BlockbusterActionDefinition.FieldType.BYTE)
                        .addField(
                                "X",
                                BlockbusterActionDefinition.FieldType.INT)
                        .addField(
                                "Y",
                                BlockbusterActionDefinition.FieldType.INT)
                        .addField(
                                "Z",
                                BlockbusterActionDefinition.FieldType.INT));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 8,
                        "mounting"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 9,
                        "interact_block")
                        .addField(
                                "X",
                                BlockbusterActionDefinition.FieldType.INT)
                        .addField(
                                "Y",
                                BlockbusterActionDefinition.FieldType.INT)
                        .addField(
                                "Z",
                                BlockbusterActionDefinition.FieldType.INT));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 10,
                        "break_block")
                        .addField(
                                "Drop",
                                BlockbusterActionDefinition.FieldType.BYTE)
                        .addField(
                                "X",
                                BlockbusterActionDefinition.FieldType.INT)
                        .addField(
                                "Y",
                                BlockbusterActionDefinition.FieldType.INT)
                        .addField(
                                "Z",
                                BlockbusterActionDefinition.FieldType.INT));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 12,
                        "morph"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 13,
                        "attack")
                        .addField(
                                "Damage",
                                BlockbusterActionDefinition.FieldType.FLOAT));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 14,
                        "damage"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 15,
                        "morph_action"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 16,
                        "command"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 17,
                        "break_animation"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 18,
                        "use_item"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 19,
                        "use_item_block")
                        .addField(
                                "Facing",
                                BlockbusterActionDefinition.FieldType.BYTE)
                        .addField(
                                "Hand",
                                BlockbusterActionDefinition.FieldType.BYTE)
                        .addField(
                                "HitX",
                                BlockbusterActionDefinition.FieldType.FLOAT)
                        .addField(
                                "HitY",
                                BlockbusterActionDefinition.FieldType.FLOAT)
                        .addField(
                                "HitZ",
                                BlockbusterActionDefinition.FieldType.FLOAT)
                        .addField(
                                "PosX",
                                BlockbusterActionDefinition.FieldType.INT)
                        .addField(
                                "PosY",
                                BlockbusterActionDefinition.FieldType.INT)
                        .addField(
                                "PosZ",
                                BlockbusterActionDefinition.FieldType.INT));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 20,
                        "use_gun"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 21,
                        "hotbar_change")
                        .addField(
                                "ItemStack",
                                BlockbusterActionDefinition.FieldType.COMPOUND)
                        .addField(
                                "Slot",
                                BlockbusterActionDefinition.FieldType.INT));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 22,
                        "interact_entity"));

        this.register(
                new BlockbusterActionDefinition(
                        (byte) 23,
                        "close_container"));
    }

    public void register(BlockbusterActionDefinition definition)
    {
        if (definition == null)
        {
            return;
        }

        this.definitions.put(
                definition.getType(),
                definition);
    }

    public BlockbusterActionDefinition get(byte type)
    {
        return this.definitions.get(type);
    }

    public BlockbusterActionDefinition get(String name)
    {
        if (name == null)
        {
            return null;
        }

        for (BlockbusterActionDefinition definition : this.definitions.values())
        {
            if (name.equals(definition.getName()))
            {
                return definition;
            }
        }

        return null;
    }

    public boolean contains(byte type)
    {
        return this.definitions.containsKey(type);
    }

    public Collection<BlockbusterActionDefinition> getAll()
    {
        return this.definitions.values();
    }

    public int size()
    {
        return this.definitions.size();
    }
}