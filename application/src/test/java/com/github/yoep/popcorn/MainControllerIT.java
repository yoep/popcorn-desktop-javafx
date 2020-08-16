package com.github.yoep.popcorn;

import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxAssert;
import org.testfx.api.FxToolkit;
import org.testfx.matcher.base.NodeMatchers;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.TimeoutException;

public class MainControllerIT extends TestFxBase {
    static final String CONTENT_PANE_ID = "#contentPane";
    static final String PLAYER_PANE_ID = "#playerPane";

    @Override
    void setUp() {
        //no-op
    }

    @Override
    public void init() throws Exception {
        FxToolkit.registerStage(Stage::new);
    }

    @Override
    public void stop() throws Exception {
        FxToolkit.hideStage();
    }

    @Test
    public void testStartup_whenNoNonOptionArgumentAreGiven_shouldShowContentAsStartScreen() throws TimeoutException {
        application = FxToolkit.setupApplication(PopcornTimeApplicationTest.class);
        FxToolkit.showStage();
        WaitForAsyncUtils.waitForFxEvents(100);

        FxAssert.verifyThat(CONTENT_PANE_ID, NodeMatchers.isNotNull());
    }

    @Test
    public void testStartup_whenNonOptionArgumentIsGiven_shouldShowPlayerAsStartScreen() throws TimeoutException {
        application = FxToolkit.setupApplication(PopcornTimeApplicationTest.class, "https://www.youtube.com/watch?v=BSF5yoD-vC4");
        FxToolkit.showStage();
        WaitForAsyncUtils.waitForFxEvents(100);

        FxAssert.verifyThat(PLAYER_PANE_ID, NodeMatchers.isNotNull());
    }
}