package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlockbusterActionDefinition
{
    public enum FieldType
    {
        BYTE,
        SHORT,
        INT,
        FLOAT,
        DOUBLE,
        BOOLEAN,
        STRING,
        COMPOUND
    }

    private final byte type;
    private final String name;
    private final List<Field> fields;

    public BlockbusterActionDefinition(
            byte type,
            String name)
    {
        this.type = type;
        this.name = name;
        this.fields = new ArrayList<>();
    }

    public BlockbusterActionDefinition addField(
            String name,
            FieldType type)
    {
        this.fields.add(new Field(name, type));

        return this;
    }

    public byte getType()
    {
        return this.type;
    }

    public String getName()
    {
        return this.name;
    }

    public List<Field> getFields()
    {
        return Collections.unmodifiableList(this.fields);
    }

    public static class Field
    {
        private final String name;
        private final FieldType type;

        public Field(
                String name,
                FieldType type)
        {
            this.name = name;
            this.type = type;
        }

        public String getName()
        {
            return this.name;
        }

        public FieldType getType()
        {
            return this.type;
        }
    }
}