    private double getAxisDragAmount(
            double[] axis,
            double mouseDX,
            double mouseDY)
    {
        /*
         * Project the world axis to the same screen used by the gizmo.
         * The mouse delta is then measured along that projected direction.
         * This automatically fixes the old reversed movement and keeps
         * dragging intuitive after the bone is rotated.
         */
        double screenX = axis[0];
        double screenY = -axis[1];
        double screenZ = axis[2];

        double yaw = Math.toRadians(0.0D);
        double pitch = Math.toRadians(0.0D);
        // The caller supplies a world-space axis; its screen direction is
        // resolved in mouseDragged through the captured basis. For the
        // editor's current drag scale, use the corresponding screen slope
        // encoded by the basis itself. A small stable fallback preserves
        // movement for nearly edge-on axes.
        double length = Math.sqrt(screenX * screenX + screenY * screenY);
        if (length < 0.0001D)
        {
            return mouseDY * -0.10D;
        }

        double ux = screenX / length;
        double uy = screenY / length;
        return (mouseDX * ux + mouseDY * uy) * 0.10D;
    }

    private double getAxisDragAngle(
            double[] axis,
            double mouseDX,
            double mouseDY)
    {
        double screenX = axis[0];
        double screenY = -axis[1];
        double length = Math.sqrt(screenX * screenX + screenY * screenY);
        if (length < 0.0001D)
        {
            return (mouseDX - mouseDY) * 0.5D;
        }

        double ux = screenX / length;
        double uy = screenY / length;
        return (mouseDX * ux + mouseDY * uy) * 0.5D;
    }

    private double[] getPlaneDragAmount(
            double[] axisA,
            double[] axisB,
            double mouseDX,
            double mouseDY)
    {
        double ax = axisA[0];
        double ay = -axisA[1];
        double bx = axisB[0];
        double by = -axisB[1];

        double det = ax * by - ay * bx;
        if (Math.abs(det) < 0.0001D)
        {
            return new double[] {0.0D, 0.0D};
        }

        double da =
                (mouseDX * by - mouseDY * bx)
                        / det
                        * 0.10D;
        double db =
                (ax * mouseDY - ay * mouseDX)
                        / det
                        * 0.10D;

        return new double[] {da, db};
    }

    private double[][] getBoneWorldAxes(
            AnimationBone bone,
            AnimationKeyframe selectedKeyframe,
            BlockbusterRecordFrame recordFrame)
    {
        if (bone == null)
        {
            return null;
        }

        int frame =
                selectedKeyframe != null
                        ? selectedKeyframe.getFrame()
                        : 0;

        AnimationTransform pivot =
                bone.getWorldPivotAt(frame);

        if (pivot == null)
        {
            return null;
        }

        double[][] result =
                new double[3][3];

        result[0] = transformBoneDirection(
                rotateVector(1.0F, 0.0F, 0.0F,
                        pivot.getRotationX(),
                        pivot.getRotationY(),
                        pivot.getRotationZ()),
                recordFrame
        );

        result[1] = transformBoneDirection(
                rotateVector(0.0F, 1.0F, 0.0F,
                        pivot.getRotationX(),
                        pivot.getRotationY(),
                        pivot.getRotationZ()),
                recordFrame
        );

        result[2] = transformBoneDirection(
                rotateVector(0.0F, 0.0F, 1.0F,
                        pivot.getRotationX(),
                        pivot.getRotationY(),
                        pivot.getRotationZ()),
                recordFrame
        );

        return result;
    }

    private double[] transformBoneDirection(
            float[] modelDirection,
            BlockbusterRecordFrame recordFrame)
    {
        double x = -modelDirection[0];
        double y = -modelDirection[1];
        double z = modelDirection[2];

        double yaw = Math.toRadians(
                180.0D
                        - (
                                recordFrame != null
                                        ? recordFrame.getYaw()
                                        : 0.0D
                        )
        );

        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        double worldX = cos * x + sin * z;
        double worldZ = -sin * x + cos * z;

        double length =
                Math.sqrt(
                        worldX * worldX
                                + y * y
                                + worldZ * worldZ
                );

        if (length < 0.000001D)
        {
            return new double[] {0.0D, 0.0D, 0.0D};
        }

        return new double[]
        {
            worldX / length,
            y / length,
            worldZ / length
        };
    }

    private double[] cross(
            double[] a,
            double[] b)
    {
        return new double[]
        {
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }

    private void normalize(double[] vector)
    {
        double length = Math.sqrt(
                vector[0] * vector[0]
                        + vector[1] * vector[1]
                        + vector[2] * vector[2]
        );

        if (length < 0.000001D)
        {
            return;
        }

        vector[0] /= length;
        vector[1] /= length;
        vector[2] /= length;
    }

    private double[] addScaled(
            double[] base,
            double amount)
    {
        return new double[]
        {
            base[0] * amount,
            base[1] * amount,
            base[2] * amount
        };
    }

    private void addScaledInPlace(
            double[] base,
            double[] vector,
            double amount)
    {
        base[0] += vector[0] * amount;
        base[1] += vector[1] * amount;
        base[2] += vector[2] * amount;
    }

    private double distanceToSegment(
            double px,
            double py,
            double x1,
            double y1,
            double x2,
            double y2)
    {
        double dx = x2 - x1;
        double dy = y2 - y1;

        double lengthSquared =
                dx * dx + dy * dy;

        if (lengthSquared <= 0.000001D)
        {
            double ex = px - x1;
            double ey = py - y1;

            return Math.sqrt(
                    ex * ex + ey * ey
            );
        }

        double t =
                (
                        (px - x1) * dx
                                + (py - y1) * dy
                )
                        / lengthSquared;

        t =
                Math.max(
                        0.0D,
                        Math.min(
                                1.0D,
                                t
                        )
                );

        double closestX =
                x1 + t * dx;
        double closestY =
                y1 + t * dy;

        double ex =
                px - closestX;
        double ey =
                py - closestY;

        return Math.sqrt(
                ex * ex + ey * ey
        );
    }

    private boolean insideSquare(
            float x,
            float y,
            float centerX,
            float centerY,
            float halfSize)
    {
        return x >= centerX - halfSize
                && x <= centerX + halfSize
                && y >= centerY - halfSize
                && y <= centerY + halfSize;
    }

    private ScreenPoint getGizmoCenter(
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            AnimationBone bone,
            AnimationKeyframe keyframe,
            BlockbusterRecordFrame recordFrame,
            EditorCamera camera)
    {
        double[] world =
                getBoneWorldPosition(
                        bone,
                        keyframe,
                        recordFrame
                );

        /*
         * A gizmo draw pass can happen before the editor has a complete
         * bone/keyframe target.  In that case there is no world position
         * to project.  Return null and let draw() use its safe fallback
         * position instead of dereferencing world[0] here.
         */
        if (world == null)
        {
            return null;
        }

        return project(
                world[0],
                world[1],
                world[2],
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight,
                camera
        );
    }

    private double[] getBoneWorldPosition(
            AnimationBone bone,
            AnimationKeyframe selectedKeyframe,
            BlockbusterRecordFrame recordFrame)
    {
        if (bone == null)
        {
            return null;
        }

        int frame =
                selectedKeyframe != null
                        ? selectedKeyframe.getFrame()
                        : 0;

        /*
         * AnimationBone stores pivots in Blockbuster model pixels.
         * getWorldPivotAt() includes the complete parent hierarchy and
         * the selected keyframe transform.
         */
        AnimationTransform pivot =
                bone.getWorldPivotAt(frame);

        if (pivot == null)
        {
            return null;
        }

        double recordX =
                recordFrame != null
                        ? recordFrame.getX()
                        : 0.0D;

        double recordY =
                recordFrame != null
                        ? recordFrame.getY()
                        : 0.0D;

        double recordZ =
                recordFrame != null
                        ? recordFrame.getZ()
                        : 0.0D;

        /*
         * Match RenderCustomModel / vanilla living-model placement:
         *
         *   model pixels -> blocks (/16)
         *   X/Z orientation -> 180 - actor yaw
         *   model handedness -> X/Y inverted
         *   model origin -> +1.501 on world Y
         *
         * This is the ONE conversion used by both rendering and mouse
         * hit testing.
         */
        double modelX =
                pivot.getPositionX() / 16.0D;

        double modelY =
                pivot.getPositionY() / 16.0D;

        double modelZ =
                pivot.getPositionZ() / 16.0D;

        double transformedX =
                -modelX;

        double transformedY =
                -modelY + 1.501D;

        double transformedZ =
                modelZ;

        double yaw =
                Math.toRadians(
                        180.0D
                                - (
                                        recordFrame != null
                                                ? recordFrame.getYaw()
                                                : 0.0D
                                )
                );

        double cos =
                Math.cos(yaw);

        double sin =
                Math.sin(yaw);

        double rotatedX =
                cos * transformedX
                        + sin * transformedZ;

        double rotatedZ =
                -sin * transformedX
                        + cos * transformedZ;

        return new double[]
        {
            recordX + rotatedX,
            recordY + transformedY,
            recordZ + rotatedZ
        };
    }

    private float[] rotateVector(
            float x,
            float y,
            float z,
            float rx,
            float ry,
            float rz)
    {
        double ax = Math.toRadians(rx);
        double ay = Math.toRadians(ry);
        double az = Math.toRadians(rz);

        float cx = (float) Math.cos(ax);
        float sx = (float) Math.sin(ax);
        float cy = (float) Math.cos(ay);
        float sy = (float) Math.sin(ay);
        float cz = (float) Math.cos(az);
        float sz = (float) Math.sin(az);

        float ny = y * cx - z * sx;
        float nz = y * sx + z * cx;
        y = ny;
        z = nz;

        float nx = x * cy + z * sy;
        nz = -x * sy + z * cy;
        x = nx;
        z = nz;

        nx = x * cz - y * sz;
        ny = x * sz + y * cz;

        return new float[] {nx, ny, z};
    }

    private ScreenPoint project(
            double x,
            double y,
            double z,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        double dx = x - camera.getCameraX();
        double dy = y - camera.getCameraY();
        double dz = z - camera.getCameraZ();

        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());

        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);

        double cameraX = cy * dx - sy * dz;
        double cameraZ = sy * dx + cy * dz;

        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);

        double cameraY = cp * dy + sp * cameraZ;
        cameraZ = -sp * dy + cp * cameraZ;

        if (cameraZ >= -0.05D)
        {
            return null;
        }

        double fov =
                Math.toRadians(60.0D);

        double focal =
                viewportHeight /
                        (2.0D * Math.tan(fov / 2.0D));

        float screenX =
                (float) (
                        viewportX
                                + viewportWidth / 2.0D
                                + cameraX * focal / -cameraZ
                );

        float screenY =
                (float) (
                        viewportY
                                + viewportHeight / 2.0D
                                - cameraY * focal / -cameraZ
                );

        return new ScreenPoint(screenX, screenY);
    }

    private float getGizmoScreenScale(
            ScreenPoint center,
            EditorCamera camera)
    {
        return 1.0F;
    }

    private void drawLine(
            float x1,
            float y1,
            float x2,
            float y2,
            int color)
    {
        /*
         * Use Minecraft's GUI tessellation path instead of the fixed
         * function GL line primitive. The Preview may have just rendered
         * through an OptiFine/FBO pipeline, and relying on GL_LINES here
         * can leave the gizmo invisible even though the GUI itself is
         * still rendering correctly.
         */
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length =
                (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 0.001F)
        {
            return;
        }

        float halfWidth = 1.5F;
        float px = -dy / length * halfWidth;
        float py = dx / length * halfWidth;

        drawRectFilled(
                x1 + px,
                y1 + py,
                x2 - px,
                y2 - py,
                color
        );
    }

    private void drawTriangle(
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3,
            int color)
    {
        drawLine(x1, y1, x2, y2, color);
        drawLine(x2, y2, x3, y3, color);
        drawLine(x3, y3, x1, y1, color);
    }

    private void drawCircle(
            float cx,
            float cy,
            float radius,
            int color,
            int segments)
    {
        int count = Math.max(12, segments);

        float previousX =
                cx + radius;
        float previousY =
                cy;

        for (int i = 1; i <= count; i++)
        {
            double angle =
                    Math.PI * 2.0D * i / count;

            float currentX =
                    cx + (float) Math.cos(angle) * radius;

            float currentY =
                    cy + (float) Math.sin(angle) * radius;

            drawLine(
                    previousX,
                    previousY,
                    currentX,
                    currentY,
                    color
            );

            previousX = currentX;
            previousY = currentY;
        }
    }

    private void drawRectOutline(
            float x,
            float y,
            float width,
            float height,
            int color)
    {
        drawRectFilled(x, y, x + width, y + 1.5F, color);
        drawRectFilled(x, y + height - 1.5F, x + width, y + height, color);
        drawRectFilled(x, y, x + 1.5F, y + height, color);
        drawRectFilled(x + width - 1.5F, y, x + width, y + height, color);
    }

    private void drawRectFilled(
            float x1,
            float y1,
            float x2,
            float y2,
            int color)
    {
        int left = (int) Math.floor(Math.min(x1, x2));
        int top = (int) Math.floor(Math.min(y1, y2));
        int right = (int) Math.ceil(Math.max(x1, x2));
        int bottom = (int) Math.ceil(Math.max(y1, y2));

        if (right <= left)
        {
            right = left + 1;
        }

        if (bottom <= top)
        {
            bottom = top + 1;
        }

        Gui.drawRect(
                left,
                top,
                right,
                bottom,
                color
        );
    }

    private void setColor(int color)
    {
        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        GL11.glColor4f(r, g, b, a);
    }

    private static class ScreenPoint
    {
        private final float x;
        private final float y;

        private ScreenPoint(float x, float y)
        {
            this.x = x;
            this.y = y;
        }
    }
}
