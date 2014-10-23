/*
 * Copyright 2000-2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.tests.components.grid.tutorials;

import com.vaadin.tests.widgetset.client.grid.tutorials.CustomRendererConenctorRpc;
import com.vaadin.ui.components.grid.AbstractRenderer;

public abstract class CustomRenderer extends AbstractRenderer<Boolean> {

    protected CustomRenderer() {
        super(Boolean.class);
        registerRpc(new CustomRendererConenctorRpc() {
            @Override
            public void change(String rowKey, boolean checked) {
                onChange(getItemId(rowKey), checked);
            }
        });
    }

    protected abstract void onChange(Object itemId, boolean checked);

}
