package myau.ui.clickgui.components.impl;

import myau.ui.clickgui.components.Component;
import myau.util.font.Font;
import myau.util.font.Fonts;
import net.minecraft.client.Minecraft;
import java.awt.Color;

public class OnlineConfigComponent extends Component {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final CategoryComponent categoryComponent;
    private final String title;
    private final String subtitle;
    private final Runnable clickAction;
    public float o;
    public float x;
    private float y;

    public OnlineConfigComponent(CategoryComponent categoryComponent, float o, String title, String subtitle, Runnable clickAction) {
        this.categoryComponent = categoryComponent;
        this.title = title;
        this.subtitle = subtitle;
        this.clickAction = clickAction;
        this.o = o;
    }

    @Override
    public void render() {
        Font titleRenderer = Fonts.MINECRAFT.get(20);
        Font subRenderer = Fonts.MINECRAFT.get(16);
        
        boolean actionable = this.clickAction != null;
        int color = actionable ? Color.WHITE.getRGB() : new Color(150, 150, 150).getRGB();

        // Draw title
        titleRenderer.draw(this.title, this.categoryComponent.getX() + 4, this.categoryComponent.getY() + this.o + 3, color, false);
        
        // Draw subtitle
        if (this.subtitle != null && !this.subtitle.isEmpty()) {
            subRenderer.draw(this.subtitle, this.categoryComponent.getX() + 4, this.categoryComponent.getY() + this.o + 13, new Color(125, 125, 125).getRGB(), false);
        }
    }

    @Override
    public void updateHeight(float n) {
        this.o = n;
    }

    @Override
    public float getHeightF() {
        return 24f;
    }

    @Override
    public float getOffset() {
        return this.o;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        this.y = this.categoryComponent.getModuleY() + this.o;
        this.x = this.categoryComponent.getX();
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.clickAction != null && this.isHovered(mouseX, mouseY) && this.categoryComponent.opened) {
            this.clickAction.run();
            return true;
        }
        return false;
    }

    private boolean isHovered(int mouseX, int mouseY) {
        return mouseX > this.x && mouseX < this.x + this.categoryComponent.getWidth()
                && mouseY > this.y && mouseY < this.y + this.getHeightF();
    }
}
