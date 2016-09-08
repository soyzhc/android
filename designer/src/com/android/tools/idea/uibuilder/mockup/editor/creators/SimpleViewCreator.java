/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.uibuilder.mockup.editor.creators;

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupCoordinate;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.SdkConstants.VIEW;

/**
 * Create a simple {@value com.android.SdkConstants#VIEW} tag with the size, mockup, and tools position attributes
 */
public class SimpleViewCreator extends BaseWidgetCreator {

  @MockupCoordinate private final Rectangle myBounds;
  @AndroidCoordinate Rectangle myTransformedBounds = new Rectangle();

  /**
   * Create a simple {@value com.android.SdkConstants#VIEW} tag
   * with the size, mockup, and tools position attributes
   *
   * @param mockup     the mockup to extract the information from
   * @param model      the model to insert the new component into
   * @param screenView The currentScreen view displayed in the {@link com.android.tools.idea.uibuilder.surface.DesignSurface}.
   *                   Used to convert the size of component from the mockup to the Android coordinates.
   * @param selection The selection made in the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   */
  public SimpleViewCreator(@NotNull Mockup mockup, @NotNull NlModel model, @NotNull ScreenView screenView, @NotNull Rectangle selection) {
    super(mockup, model, screenView);
    myBounds = selection;

    Rectangle cropping = getMockup().getRealCropping();
    final NlComponent component = getMockup().getComponent();
    final float xScale = component.w / (float)cropping.width;
    final float yScale = component.h / (float)cropping.height;
    myTransformedBounds.setBounds(Math.round(xScale * myBounds.x),
                                  Math.round(yScale * myBounds.y),
                                  Math.round(xScale * myBounds.width),
                                  Math.round(yScale * myBounds.height));
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public String getAndroidViewTag() {
    return VIEW;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void addAttributes(@NotNull AttributesTransaction transaction) {
    addLayoutEditorPositionAttribute(transaction, myTransformedBounds);
    addSizeAttributes(transaction, myTransformedBounds);
    addMockupAttributes(transaction, myBounds);
  }

  /**
   * {@inheritDoc}
   */
  public Rectangle getTransformedBounds() {
    return myTransformedBounds;
  }

  /**
   * {@inheritDoc}
   */
  public Rectangle getBounds() {
    return myBounds;
  }
}
