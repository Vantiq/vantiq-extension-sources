package edu.ml.tensorflow.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Model to store the position of the bounding boxes
 */
public class BoxPosition {
    private float left;
    private float top;
    private float right;
    private float bottom;
    private float width;
    private float height;
    private float centerX;
    private float centerY;


    /**
     * Build from center point + height & width
     *
     * @param x float X coordinate of the center
     * @param y float Y coordinate of the center
     * @param width float width of box
     * @param height float height of box
     */
    public BoxPosition(float x, float y, float width, float height) {

        this.left = x - width / 2;
        this.top = y - height / 2;
        this.width = width;
        this.height = height;
        this.centerX = x;
        this.centerY = y;

        init();
    }

    public BoxPosition(final BoxPosition boxPosition) {
        this.left = boxPosition.left;
        this.top = boxPosition.top;
        this.width = boxPosition.width;
        this.height = boxPosition.height;
        this.centerX = boxPosition.centerX;
        this.centerY = boxPosition.centerY;

        init();
    }

    public BoxPosition(final BoxPosition boxPosition, final float scaleX, final float scaleY) {
        this.left = boxPosition.left * scaleX;
        this.top = boxPosition.top * scaleY;
        this.width = boxPosition.width * scaleX;
        this.height = boxPosition.height * scaleY;
        this.centerX = boxPosition.centerX * scaleX;
        this.centerY = boxPosition.centerY * scaleY;

        init();
    }

    public void init() {
        float tmpLeft = this.left;
        float tmpTop = this.top;
        float tmpRight = this.left + this.width;
        float tmpBottom = this.top + this.height;

        this.left = Math.min(tmpLeft, tmpRight); // left should have lower value as right
        this.top = Math.min(tmpTop, tmpBottom);  // top should have lower value as bottom
        this.right = Math.max(tmpLeft, tmpRight);
        this.bottom = Math.max(tmpTop, tmpBottom);
    }

    public float getLeft() {
        return left;
    }

    public int getLeftInt() {
        return (int) left;
    }

    public float getTop() {
        return top;
    }

    public int getTopInt() {
        return (int) top;
    }

    public float getWidth() {
        return width;
    }

    public int getWidthInt() {
        return (int) width;
    }

    public float getHeight() {
        return height;
    }

    public int getHeightInt() {
        return (int) height;
    }

    public float getRight() {
        return right;
    }

    public int getRightInt() {
        return (int) right;
    }

    public float getBottom() {
        return bottom;
    }

    public int getBottomInt() {
        return (int) bottom;
    }

    public float getCenterX() {
        return centerX;
    }

    public int getCenterXInt() {
        return (int) centerX;
    }
    public float getCenterY() {
        return centerY;
    }

    public int getCenterYInt() {
        return (int) centerY;
    }

    public Map asExternalMap() {
        HashMap<String, Object> location = new HashMap<>();

        location.put("left", getLeft());
        location.put("top", getTop());
        location.put("right", getRight());
        location.put("bottom", getBottom());
        location.put("centerX", getCenterX());
        location.put("centerY", getCenterY());

        return location;
    }
    
    @Override
    public String toString() {
        return "BoxPosition{" +
                "left=" + left +
                ", top=" + top +
                ", width=" + width +
                ", height=" + height +
                '}';
    }
}
