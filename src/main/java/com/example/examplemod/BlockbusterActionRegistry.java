package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class BlockbusterActionRegistry
{
    private final Map<Byte, String> actions =
            new TreeMap<Byte, String>();
    private BlockbusterActionDefinitions definitions;

    /**
     * Создать пустой реестр.
     */
    public BlockbusterActionRegistry()
    {
        this.definitions =
                new BlockbusterActionDefinitions();
    }

    /**
     * Загрузить реестр из корневого NBT-тега Actions.
     *
     * В оригинальном Blockbuster ключ NBT является числовым ID
     * действия, а значение — его строковым названием.
     *
     * Например:
     *
     * "21" -> "hotbar_change"
     */
    public static BlockbusterActionRegistry fromNBT(NBTTagCompound nbt)
    {
        BlockbusterActionRegistry registry =
                new BlockbusterActionRegistry();

        if (nbt == null)
        {
            return registry;
        }

        Set<String> keys = nbt.getKeySet();

        for (String key : keys)
        {
            try
            {
                int id = Integer.parseInt(key);

                /*
                 * Actions в оригинальном Blockbuster —
                 * строковые значения.
                 */
                if (nbt.hasKey(key, 8))
                {
                    String name = nbt.getString(key);

                    registry.actions.put(
                            (byte) id,
                            name
                    );
                }
            }
            catch (NumberFormatException ignored)
            {
                /*
                 * Неизвестный ключ просто пропускаем.
                 */
            }
        }

        return registry;
    }

    /**
     * Добавить или заменить действие.
     */
    public void register(byte id, String name)
    {
        if (name == null)
        {
            name = "";
        }

        this.actions.put(id, name);
    }

    /**
     * Получить название действия по ID.
     */
    public String getName(byte id)
    {
        return this.actions.get(id);
    }

    /**
     * Проверить наличие действия.
     */
    public boolean contains(byte id)
    {
        return this.actions.containsKey(id);
    }

    /**
     * Получить количество зарегистрированных действий.
     */
    public int size()
    {
        return this.actions.size();
    }

    /**
     * Получить NBT-представление реестра.
     *
     * Структура полностью соответствует оригинальному
     * Blockbuster:
     *
     * Actions
     * ├── 1 = "chat"
     * ├── 2 = "swipe"
     * └── ...
     */
    public NBTTagCompound toNBT()
    {
        NBTTagCompound nbt =
                new NBTTagCompound();

        for (Map.Entry<Byte, String> entry :
                this.actions.entrySet())
        {
            int id = entry.getKey();

            /*
             * Byte может быть отрицательным для значений >127,
             * поэтому здесь приводим его к unsigned ID.
             *
             * Для текущих Blockbuster actions это особенно важно
             * как общая защита модели.
             */
            if (id < 0)
            {
                id += 256;
            }

            nbt.setString(
                    String.valueOf(id),
                    entry.getValue()
            );
        }

        return nbt;
    }

    /**
     * Получить все зарегистрированные действия.
     */
    public Map<Byte, String> getActions()
    {
        return this.actions;
    }

    /**
     * Очистить реестр.
     */
    public void clear()
    {
        this.actions.clear();
    }
    /**
     * Получить описание действия по его числовому ID.
     */
    public BlockbusterActionDefinition getDefinition(byte id)
    {
        return this.definitions.get(id);
    }

    /**
     * Получить описание действия по его имени.
     */
    public BlockbusterActionDefinition getDefinition(String name)
    {
        return this.definitions.get(name);
    }

    /**
     * Получить каталог описаний всех известных действий.
     */
    public BlockbusterActionDefinitions getDefinitions()
    {
        return this.definitions;
    }
}