package com.example.examplemod;

public class EditorTimeline
{
    /**
     * В Minecraft 20 игровых тиков = 1 секунда.
     */
    public static final int TICKS_PER_SECOND = 20;

    private int currentTick;
    private int length;

    private boolean playing;

    /**
     * Скорость воспроизведения.
     *
     * 1.0 = реальное время.
     * 0.5 = половина скорости.
     * 2.0 = двойная скорость.
     */
    private float playbackSpeed;

    private float playbackAccumulator;

    public EditorTimeline()
    {
        this.currentTick = 0;
        this.length = 0;

        this.playing = false;

        this.playbackSpeed = 1.0F;
        this.playbackAccumulator = 0.0F;
    }

    /**
     * Установить длину временной шкалы в тиках.
     */
    public void setLength(int length)
    {
        if (length < 0)
        {
            length = 0;
        }

        this.length = length;

        if (this.currentTick > this.getLastTick())
        {
            this.currentTick = this.getLastTick();
        }
    }

    /**
     * Получить длину временной шкалы в тиках.
     */
    public int getLength()
    {
        return this.length;
    }

    /**
     * Получить последний допустимый тик.
     */
    public int getLastTick()
    {
        if (this.length <= 0)
        {
            return 0;
        }

        return this.length - 1;
    }

    /**
     * Установить текущий тик.
     */
    public void setTick(int tick)
    {
        if (tick < 0)
        {
            tick = 0;
        }

        if (tick > this.getLastTick())
        {
            tick = this.getLastTick();
        }

        this.currentTick = tick;
        this.playbackAccumulator = 0.0F;
    }

    /**
     * Получить текущий тик.
     */
    public int getTick()
    {
        return this.currentTick;
    }

    /**
     * Получить текущую позицию в секундах.
     *
     * Например:
     *
     * 0  -> 0.0 сек
     * 10 -> 0.5 сек
     * 20 -> 1.0 сек
     * 40 -> 2.0 сек
     */
    public float getCurrentSeconds()
    {
        return (float) this.currentTick /
                (float) TICKS_PER_SECOND;
    }

    /**
     * Получить длительность записи в секундах.
     */
    public float getLengthSeconds()
    {
        return (float) this.length /
                (float) TICKS_PER_SECOND;
    }

    /**
     * Получить длительность последнего тика в секундах.
     */
    public float getLastTickSeconds()
    {
        return (float) this.getLastTick() /
                (float) TICKS_PER_SECOND;
    }

    /**
     * Перейти на следующий тик.
     */
    public void nextTick()
    {
        if (this.currentTick < this.getLastTick())
        {
            this.currentTick++;
        }
    }

    /**
     * Перейти на предыдущий тик.
     */
    public void previousTick()
    {
        if (this.currentTick > 0)
        {
            this.currentTick--;
        }
    }

    /**
     * Перейти в начало.
     */
    public void rewind()
    {
        this.currentTick = 0;
        this.playbackAccumulator = 0.0F;
    }

    /**
     * Перейти в конец.
     */
    public void end()
    {
        this.currentTick = this.getLastTick();
        this.playbackAccumulator = 0.0F;
    }

    /**
     * Начать воспроизведение.
     */
    public void play()
    {
        if (this.length <= 0)
        {
            return;
        }

        this.playing = true;
    }

    /**
     * Остановить воспроизведение.
     */
    public void pause()
    {
        this.playing = false;
        this.playbackAccumulator = 0.0F;
    }

    /**
     * Переключить состояние воспроизведения.
     */
    public void togglePlaying()
    {
        if (this.playing)
        {
            this.pause();
        }
        else
        {
            this.play();
        }
    }

    /**
     * Проверить, проигрывается ли таймлайн.
     */
    public boolean isPlaying()
    {
        return this.playing;
    }

    /**
     * Установить скорость воспроизведения.
     *
     * 1.0 = нормальная скорость.
     * 0.5 = половина.
     * 2.0 = двойная.
     */
    public void setPlaybackSpeed(float speed)
    {
        if (speed <= 0.0F)
        {
            speed = 1.0F;
        }

        this.playbackSpeed = speed;
    }

    /**
     * Получить скорость воспроизведения.
     */
    public float getPlaybackSpeed()
    {
        return this.playbackSpeed;
    }

    /**
     * Обновить таймлайн на один игровой тик.
     *
     * При speed = 1.0:
     *
     * 20 обновлений = 20 тиков записи = 1 секунда.
     *
     * При speed = 2.0:
     *
     * 20 обновлений = 40 тиков записи = 2 секунды
     * анимации проходят за 1 секунду.
     */
    public void update()
    {
        if (!this.playing || this.length <= 0)
        {
            return;
        }

        this.playbackAccumulator +=
                this.playbackSpeed;

        while (this.playbackAccumulator >= 1.0F)
        {
            this.playbackAccumulator -= 1.0F;

            if (this.currentTick < this.getLastTick())
            {
                this.currentTick++;
            }
            else
            {
                this.currentTick = 0;
                this.playbackAccumulator = 0.0F;

                break;
            }
        }
    }
}