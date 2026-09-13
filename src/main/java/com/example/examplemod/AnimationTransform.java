package com.example.examplemod;

public class AnimationTransform
{
    private float positionX;
    private float positionY;
    private float positionZ;

    private float rotationX;
    private float rotationY;
    private float rotationZ;

    private float scaleX;
    private float scaleY;
    private float scaleZ;

    public AnimationTransform()
    {
        this.positionX = 0.0F;
        this.positionY = 0.0F;
        this.positionZ = 0.0F;

        this.rotationX = 0.0F;
        this.rotationY = 0.0F;
        this.rotationZ = 0.0F;

        this.scaleX = 1.0F;
        this.scaleY = 1.0F;
        this.scaleZ = 1.0F;
    }

    /*
     * ---------------------------------------------------------
     * POSITION
     * ---------------------------------------------------------
     */

    public float getPositionX()
    {
        return this.positionX;
    }

    public float getPositionY()
    {
        return this.positionY;
    }

    public float getPositionZ()
    {
        return this.positionZ;
    }

    public void setPosition(
            float x,
            float y,
            float z)
    {
        this.positionX = x;
        this.positionY = y;
        this.positionZ = z;
    }

    /*
     * ---------------------------------------------------------
     * ROTATION
     * ---------------------------------------------------------
     */

    public float getRotationX()
    {
        return this.rotationX;
    }

    public float getRotationY()
    {
        return this.rotationY;
    }

    public float getRotationZ()
    {
        return this.rotationZ;
    }

    public void setRotation(
            float x,
            float y,
            float z)
    {
        this.rotationX = x;
        this.rotationY = y;
        this.rotationZ = z;
    }

    /*
     * ---------------------------------------------------------
     * SCALE
     * ---------------------------------------------------------
     */

    public float getScaleX()
    {
        return this.scaleX;
    }

    public float getScaleY()
    {
        return this.scaleY;
    }

    public float getScaleZ()
    {
        return this.scaleZ;
    }

    public void setScale(
            float x,
            float y,
            float z)
    {
        this.scaleX = x;
        this.scaleY = y;
        this.scaleZ = z;
    }

    /*
     * ---------------------------------------------------------
     * COPY
     * ---------------------------------------------------------
     */

    public AnimationTransform copy()
    {
        AnimationTransform result =
                new AnimationTransform();

        result.setPosition(
                this.positionX,
                this.positionY,
                this.positionZ
        );

        result.setRotation(
                this.rotationX,
                this.rotationY,
                this.rotationZ
        );

        result.setScale(
                this.scaleX,
                this.scaleY,
                this.scaleZ
        );

        return result;
    }

    /*
     * ---------------------------------------------------------
     * COMBINE
     * ---------------------------------------------------------
     *
     * Объединяет:
     *
     * this  = transform родителя
     * child = локальный transform ребёнка
     *
     * Результат:
     *
     * parent * child
     *
     * Позиция ребёнка:
     *
     * 1. масштабируется родителем
     * 2. вращается родителем
     * 3. переносится в позицию родителя
     *
     * Вращение:
     *
     * объединяется через матрицы вращения,
     * а не простым сложением углов.
     *
     * Масштаб:
     *
     * перемножается.
     */

    public AnimationTransform combine(
            AnimationTransform child)
    {
        if (child == null)
        {
            return this.copy();
        }

        AnimationTransform result =
                new AnimationTransform();

        /*
         * -----------------------------------------------------
         * CHILD POSITION
         * -----------------------------------------------------
         */

        float x =
                child.getPositionX()
                        * this.scaleX;

        float y =
                child.getPositionY()
                        * this.scaleY;

        float z =
                child.getPositionZ()
                        * this.scaleZ;

        /*
         * Применяем вращение родителя
         * к локальной позиции ребёнка.
         */
        float[] rotated =
                rotateVector(
                        x,
                        y,
                        z,
                        this.rotationX,
                        this.rotationY,
                        this.rotationZ
                );

        /*
         * Переводим позицию ребёнка
         * в мировое пространство.
         */
        result.setPosition(
                this.positionX + rotated[0],
                this.positionY + rotated[1],
                this.positionZ + rotated[2]
        );

        /*
         * -----------------------------------------------------
         * ROTATION
         * -----------------------------------------------------
         *
         * Вращения объединяются через матрицы.
         *
         * Порядок:
         *
         * parentRotation * childRotation
         *
         * Это принципиально отличается от:
         *
         * parentX + childX
         *
         * потому что вращения в 3D
         * не являются коммутативными.
         */

        float[][] parentMatrix =
                createRotationMatrix(
                        this.rotationX,
                        this.rotationY,
                        this.rotationZ
                );

        float[][] childMatrix =
                createRotationMatrix(
                        child.getRotationX(),
                        child.getRotationY(),
                        child.getRotationZ()
                );

        float[][] worldMatrix =
                multiplyMatrix(
                        parentMatrix,
                        childMatrix
                );

        float[] worldRotation =
                matrixToEuler(
                        worldMatrix
                );

        result.setRotation(
                worldRotation[0],
                worldRotation[1],
                worldRotation[2]
        );

        /*
         * -----------------------------------------------------
         * SCALE
         * -----------------------------------------------------
         */

        result.setScale(
                this.scaleX
                        * child.getScaleX(),

                this.scaleY
                        * child.getScaleY(),

                this.scaleZ
                        * child.getScaleZ()
        );

        return result;
    }

    /*
     * ---------------------------------------------------------
     * VECTOR ROTATION
     * ---------------------------------------------------------
     *
     * Используется для перемещения точки ребёнка
     * относительно вращённого родителя.
     *
     * Порядок вращения соответствует:
     *
     * X -> Y -> Z
     */

    private static float[] rotateVector(
            float x,
            float y,
            float z,
            float rotationX,
            float rotationY,
            float rotationZ)
    {
        double rx =
                Math.toRadians(
                        rotationX
                );

        double ry =
                Math.toRadians(
                        rotationY
                );

        double rz =
                Math.toRadians(
                        rotationZ
                );

        /*
         * X
         */
        float cosX =
                (float) Math.cos(rx);

        float sinX =
                (float) Math.sin(rx);

        float rotatedY =
                y * cosX
                        - z * sinX;

        float rotatedZ =
                y * sinX
                        + z * cosX;

        y = rotatedY;
        z = rotatedZ;

        /*
         * Y
         */
        float cosY =
                (float) Math.cos(ry);

        float sinY =
                (float) Math.sin(ry);

        float rotatedX =
                x * cosY
                        + z * sinY;

        rotatedZ =
                -x * sinY
                        + z * cosY;

        x = rotatedX;
        z = rotatedZ;

        /*
         * Z
         */
        float cosZ =
                (float) Math.cos(rz);

        float sinZ =
                (float) Math.sin(rz);

        rotatedX =
                x * cosZ
                        - y * sinZ;

        rotatedY =
                x * sinZ
                        + y * cosZ;

        x = rotatedX;
        y = rotatedY;

        return new float[]
                {
                        x,
                        y,
                        z
                };
    }

    /*
     * ---------------------------------------------------------
     * ROTATION MATRIX
     * ---------------------------------------------------------
     */

    private static float[][] createRotationMatrix(
            float rotationX,
            float rotationY,
            float rotationZ)
    {
        double rx =
                Math.toRadians(
                        rotationX
                );

        double ry =
                Math.toRadians(
                        rotationY
                );

        double rz =
                Math.toRadians(
                        rotationZ
                );

        float cx =
                (float) Math.cos(rx);

        float sx =
                (float) Math.sin(rx);

        float cy =
                (float) Math.cos(ry);

        float sy =
                (float) Math.sin(ry);

        float cz =
                (float) Math.cos(rz);

        float sz =
                (float) Math.sin(rz);

        /*
         * X rotation.
         */
        float[][] matrixX =
                new float[][]
                        {
                                {
                                        1.0F, 0.0F, 0.0F
                                },
                                {
                                        0.0F, cx, -sx
                                },
                                {
                                        0.0F, sx, cx
                                }
                        };

        /*
         * Y rotation.
         */
        float[][] matrixY =
                new float[][]
                        {
                                {
                                        cy, 0.0F, sy
                                },
                                {
                                        0.0F, 1.0F, 0.0F
                                },
                                {
                                        -sy, 0.0F, cy
                                }
                        };

        /*
         * Z rotation.
         */
        float[][] matrixZ =
                new float[][]
                        {
                                {
                                        cz, -sz, 0.0F
                                },
                                {
                                        sz, cz, 0.0F
                                },
                                {
                                        0.0F, 0.0F, 1.0F
                                }
                        };

        /*
         * X -> Y -> Z
         */
        return multiplyMatrix(
                multiplyMatrix(
                        matrixZ,
                        matrixY
                ),
                matrixX
        );
    }

    /*
     * ---------------------------------------------------------
     * MATRIX MULTIPLICATION
     * ---------------------------------------------------------
     */

    private static float[][] multiplyMatrix(
            float[][] a,
            float[][] b)
    {
        float[][] result =
                new float[3][3];

        for (
                int row = 0;
                row < 3;
                row++
        )
        {
            for (
                    int column = 0;
                    column < 3;
                    column++
            )
            {
                float value = 0.0F;

                for (
                        int i = 0;
                        i < 3;
                        i++
                )
                {
                    value +=
                            a[row][i]
                                    * b[i][column];
                }

                result[row][column] =
                        value;
            }
        }

        return result;
    }

    /*
     * ---------------------------------------------------------
     * MATRIX -> EULER
     * ---------------------------------------------------------
     *
     * Преобразует итоговую матрицу вращения
     * обратно в три угла.
     *
     * Это необходимо потому, что остальные части
     * нашего редактора работают с Euler angles.
     */

    private static float[] matrixToEuler(
            float[][] matrix)
    {
        /*
         * Для используемой последовательности
         * вращений проверяем наличие gimbal lock.
         */

        float m20 =
                matrix[2][0];

        if (m20 < 1.0F
                && m20 > -1.0F)
        {
            float rotationY =
                    (float) Math.asin(
                            -m20
                    );

            float rotationX =
                    (float) Math.atan2(
                            matrix[2][1],
                            matrix[2][2]
                    );

            float rotationZ =
                    (float) Math.atan2(
                            matrix[1][0],
                            matrix[0][0]
                    );

            return new float[]
                    {
                            (float) Math.toDegrees(
                                    rotationX
                            ),

                            (float) Math.toDegrees(
                                    rotationY
                            ),

                            (float) Math.toDegrees(
                                    rotationZ
                            )
                    };
        }

        /*
         * Gimbal lock.
         *
         * В этом случае фиксируем Z
         * и восстанавливаем X.
         */

        float rotationY =
                m20 <= -1.0F
                        ? 90.0F
                        : -90.0F;

        float rotationX =
                (float) Math.atan2(
                        -matrix[0][1],
                        matrix[1][1]
                );

        return new float[]
                {
                        (float) Math.toDegrees(
                                rotationX
                        ),

                        rotationY,

                        0.0F
                };
    }
}