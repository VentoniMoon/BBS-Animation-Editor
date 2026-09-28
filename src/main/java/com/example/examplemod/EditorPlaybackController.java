package com.example.examplemod;

/**
 * Управляет временем воспроизведения редактора.
 *
 * Отвечает только за:
 * - текущий кадр;
 * - воспроизведение;
 * - паузу;
 * - переход на следующий/предыдущий тик;
 * - синхронизацию состояния с EditorTimeline.
 *
 * Контроллер НЕ знает ничего о:
 * - Actor;
 * - Preview;
 * - AnimationBone;
 * - Keyframe;
 * - Record;
 * - UI.
 */
public class EditorPlaybackController
{
    private final EditorTimeline timeline;

    private int currentFrame = 0;

    private float currentAnimationFrame = 0.0F;

    private boolean playing = false;

    public EditorPlaybackController(
            EditorTimeline timeline)
    {
        this.timeline = timeline;
    }

    /**
     * Возвращает текущий целочисленный кадр.
     */
    public int getCurrentFrame()
    {
        return this.currentFrame;
    }

    /**
     * Возвращает текущий кадр с плавающей точкой.
     *
     * Нужен для плавного воспроизведения.
     */
    public float getCurrentAnimationFrame()
    {
        return this.currentAnimationFrame;
    }

    /**
     * Возвращает состояние воспроизведения.
     */
    public boolean isPlaying()
    {
        return this.playing;
    }

    /**
     * Синхронизирует контроллер с EditorTimeline.
     *
     * Вызывается из updateScreen().
     */
    public void update()
    {
        this.currentFrame =
                this.timeline.getTick();

        this.currentAnimationFrame =
                this.timeline.getCurrentFrameFloat();

        this.playing =
                this.timeline.isPlaying();
    }

    /**
     * Переключает Play / Pause.
     */
    public void togglePlayback()
    {
        this.timeline.togglePlaying();

        this.playing =
                this.timeline.isPlaying();
    }

    /**
     * Ставит воспроизведение на паузу.
     */
    public void pause()
    {
        this.timeline.pause();

        this.playing = false;
    }

    /**
     * Переходит на один тик назад.
     */
    public void previousFrame()
    {
        this.timeline.previousTick();

        syncAfterManualStep();
    }

    /**
     * Переходит на один тик вперёд.
     */
    public void nextFrame()
    {
        this.timeline.nextTick();

        syncAfterManualStep();
    }

    /**
     * Переходит на тик в указанном направлении.
     *
     * direction < 0 — назад
     * direction >= 0 — вперёд
     */
    public void step(int direction)
    {
        this.timeline.pause();

        this.playing = false;

        if (direction < 0)
        {
            this.timeline.previousTick();
        }
        else
        {
            this.timeline.nextTick();
        }

        syncAfterManualStep();
    }

    /**
     * Синхронизация после ручного перехода по Timeline.
     */
    private void syncAfterManualStep()
    {
        this.currentFrame =
                this.timeline.getTick();

        this.currentAnimationFrame =
                (float) this.currentFrame;

        this.playing =
                this.timeline.isPlaying();
    }

    /**
     * Принудительно устанавливает текущий кадр.
     */
    public void setCurrentFrame(
            int frame)
    {
        this.timeline.setTick(frame);

        this.currentFrame =
                this.timeline.getTick();

        this.currentAnimationFrame =
                (float) this.currentFrame;

        this.playing =
                this.timeline.isPlaying();
    }

    /**
     * Возвращает Timeline, которым управляет контроллер.
     */
    public EditorTimeline getTimeline()
    {
        return this.timeline;
    }
}