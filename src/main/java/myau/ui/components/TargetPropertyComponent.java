package myau.ui.components;

import myau.enums.ChatColors;
import myau.property.properties.BooleanProperty;
import myau.ui.Component;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.atomic.AtomicInteger;

public class TargetPropertyComponent implements Component {
    private final BooleanProperty property;
    private final CategoryComponent category;
    private int offsetY;
    private int x;
    private int y;

    public TargetPropertyComponent(BooleanProperty property, CategoryComponent category, int offsetY) {
        this.property = property;
        this.category = category;
        this.offsetY = offsetY;
        this.x = category.getX();
        this.y = category.getY() + offsetY;
    }

    @Override
    public void draw(AtomicInteger offset) {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        Minecraft.getMinecraft().fontRendererObj.drawString(
                this.property.getName().replace("-", " ") + ": " + ChatColors.formatColor(this.property.formatValue()),
                (float) ((this.category.getX() + 4) * 2),
                (float) ((this.category.getY() + this.offsetY + 5) * 2),
                -1,
                false
        );
        GL11.glPopMatrix();
    }

    @Override
    public void update(int mousePosX, int mousePosY) {
        this.x = this.category.getX();
        this.y = this.category.getY() + this.offsetY;
    }

    @Override
    public void mouseDown(int x, int y, int button) {
        if (this.isHovered(x, y) && button == 0) {
            this.property.setValue(!this.property.getValue());
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
    }

    @Override
    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return 12;
    }

    @Override
    public boolean isVisible() {
        return this.property.isVisible();
    }

    private boolean isHovered(int x, int y) {
        return x > this.x && x < this.x + this.category.getWidth() && y > this.y && y < this.y + 11;
    }
}
