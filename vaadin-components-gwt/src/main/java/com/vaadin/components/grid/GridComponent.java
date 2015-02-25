package com.vaadin.components.grid;

import static com.google.gwt.query.client.GQuery.$;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayInteger;
import com.google.gwt.core.client.JsArrayMixed;
import com.google.gwt.core.client.js.JsExport;
import com.google.gwt.core.client.js.JsNamespace;
import com.google.gwt.core.client.js.JsNoExport;
import com.google.gwt.core.client.js.JsType;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TableElement;
import com.google.gwt.query.client.Function;
import com.google.gwt.query.client.GQuery;
import com.google.gwt.query.client.Properties;
import com.google.gwt.query.client.js.JsUtils;
import com.google.gwt.query.client.plugins.widgets.WidgetsUtils;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.client.data.AbstractRemoteDataSource;
import com.vaadin.client.data.DataSource;
import com.vaadin.client.renderers.Renderer;
import com.vaadin.client.widget.escalator.ColumnConfiguration;
import com.vaadin.client.widget.escalator.RowContainer;
import com.vaadin.client.widget.grid.RendererCellReference;
import com.vaadin.client.widget.grid.selection.SelectionEvent;
import com.vaadin.client.widget.grid.selection.SelectionHandler;
import com.vaadin.client.widgets.Escalator;
import com.vaadin.client.widgets.Grid;
import com.vaadin.client.widgets.Grid.HeaderCell;
import com.vaadin.client.widgets.Grid.HeaderRow;
import com.vaadin.client.widgets.Grid.SelectionMode;
import com.vaadin.components.common.util.DOMUtils;
import com.vaadin.components.common.util.Elements;
import com.vaadin.components.grid.config.JS;
import com.vaadin.components.grid.config.JSArray;
import com.vaadin.components.grid.config.JSColumn;
import com.vaadin.components.grid.config.JSHeaderCell;
import com.vaadin.components.grid.config.JSHeaderCell.Format;
import com.vaadin.components.grid.data.GridDataSource;
import com.vaadin.components.grid.data.GridDomTableDataSource;
import com.vaadin.components.grid.data.GridJsFuncDataSource;
import com.vaadin.components.grid.data.GridJsObjectDataSource;
import com.vaadin.shared.ui.grid.HeightMode;

@JsNamespace(Elements.VAADIN_JS_NAMESPACE)
@JsExport
@JsType
public class GridComponent implements SelectionHandler<JsArrayMixed> {

    private Grid<JsArrayMixed> grid;
    public JSArray<JSColumn> cols;
    private boolean changed = true;
    // FIXME: using columns name here make this fail in prod mode
    private List<Grid.Column<Object, JsArrayMixed>> gridColumns;
    private List<Grid.Column<Object, JsArrayMixed>> JstColumns;

    // Array of JSO representing column configuration
    // used in JS to change renderers.
    private JSArray<JSColumn> columnsJso;

    private GQuery lightDom;

    private boolean updating = false;

    private int headerDefaultRowIndex = 0;

    public GridComponent() {
        // FIXME: If there is no default constructor JsInterop does not export
        // anything
    }

    public Element getGridElement() {
        return grid.getElement();
    }

    public void setLightDom(TableElement tableElement) {
        lightDom = $(tableElement);
    }

    public void created() {
        cols = JS.createArray();
        gridColumns = new ArrayList<>();
        JstColumns = new ArrayList<>();
        grid = new Grid<JsArrayMixed>();
        grid.addSelectionHandler(this);
    }

    public void initGrid() {
        WidgetsUtils.attachWidget(grid, null);
        loadHeaders();

        if (!changed) {
            return;
        }
        changed = false;
        DataSource<JsArrayMixed> dataSource = grid.getDataSource();

        while (JstColumns.size() > 0) {
            grid.removeColumn(JstColumns.remove(0));
        }
        if (cols != null) {
            for (int i = 0, l = cols.size(); i < l; i++) {
                JSColumn c = cols.get(i);
                Grid.Column<Object, JsArrayMixed> col;
                col = createGridColumn(c, i);
                grid.addColumn(col);
                JstColumns.add(col);
                for (int j = 0; j < c.headerData().size(); j++) {
                    if (grid.getHeaderRowCount() < c.headerData().size()) {
                        grid.appendHeaderRow();
                    }
                    JSHeaderCell header = c.headerData().get(j);
                    int offset = 0;
                    for (int k = 0; k <= j + offset; k++) {
                        HeaderRow row = grid.getHeaderRow(k);
                        if (i != 0
                                && row.getCell(grid.getColumn(i - 1))
                                        .getColspan() != 1) {
                            offset++;
                        }
                    }
                    HeaderCell cell = grid.getHeaderRow(j + offset)
                            .getCell(col);
                    cell.setColspan(header.colSpan());
                    Object content = header.content();
                    switch (Format.valueOf(header.format())) {
                    case HTML:
                        cell.setHtml((String) content);
                        break;
                    case WIDGET:
                        cell.setWidget((Widget) content);
                        break;
                    case TEXT:
                        cell.setText((String) content);
                        break;
                    }
                }
            }
            grid.setDefaultHeaderRow(grid.getHeaderRow(headerDefaultRowIndex));
        }

        // If the wrapped DOM table has TR elements, we use it as data source
        dataSource = GridDomTableDataSource.createInstance(lightDom.get(0),
                this);
        if (dataSource != null) {
            grid.setDataSource(dataSource);
        }
    }

    public static Grid.Column<Object, JsArrayMixed> createGridColumn(
            final JSColumn gColumn, final int idx) {
        final RegExp templateRegexp = RegExp.compile("\\{\\{data\\}\\}", "ig");
        return new Grid.Column<Object, JsArrayMixed>(new Renderer<Object>() {
            @Override
            public void render(RendererCellReference cell, Object data) {
                Object o = gColumn.renderer();
                Element elm = cell.getElement();
                if (o instanceof JavaScriptObject) {
                    if (JsUtils.isFunction((JavaScriptObject) o)) {
                        JsUtils.jsni((JavaScriptObject) o, "call", o, elm,
                                data, cell.getRow());
                    } else {
                        if ($(elm).data("init") == null) {
                            $(elm).data("init", true);
                            JsUtils.jsni((JavaScriptObject) o, "init", elm);
                        }
                        JsUtils.jsni((JavaScriptObject) o, "render", elm, data);
                    }
                } else {
                    if (gColumn.template() != null) {
                        // FIXME: this implementation doesn't
                        // reuse any of the possible HTML tags
                        // included in the template.
                        elm.setInnerHTML(templateRegexp.replace(
                                gColumn.template(), String.valueOf(data)));
                    } else {
                        elm.setInnerHTML(String.valueOf(data));
                    }
                }
            }
        }) {
            @Override
            public Object getValue(JsArrayMixed row) {
                Object o = gColumn.value();
                if (o instanceof JavaScriptObject
                        && JsUtils.isFunction((JavaScriptObject) o)) {
                    o = JsUtils.jsni((JavaScriptObject) o, "call", o, row, idx);
                } else if (o instanceof String
                // For some reason JsInterop returns empty
                        && "" != o) {
                    o = JsUtils.prop(row, o);
                } else {
                    if (JsUtils.isArray(row)) {
                        o = row.getObject(idx);
                    } else {
                        Properties p = row.cast();
                        o = p.getObject(p.keys()[idx]);
                    }
                }
                return o;
            }
        };
    }

    private String lastHeaders = null;

    private void loadHeaders() {
        GQuery $theadRows = lightDom.find("thead tr");
        String txt = $theadRows.toString();
        if ($theadRows.isEmpty() || txt.equals(lastHeaders)) {
            return;
        }
        lastHeaders = txt;

        JSArray<JSColumn> colList = JS.createArray();

        Map<JSColumn, JSArray<JSHeaderCell>> contentsMap = new HashMap<JSColumn, JSArray<JSHeaderCell>>();

        headerDefaultRowIndex = $theadRows.index(lightDom.find("tr[default]")
                .get(0));
        if (headerDefaultRowIndex == -1) {
            headerDefaultRowIndex = 0;
        }
        for (int i = 0; i < $theadRows.size(); i++) {
            GQuery $ths = $theadRows.eq(i).children("th");
            while (colList.size() < $ths.size()) {
                JSColumn column = JS.createJsType(JSColumn.class);
                contentsMap.put(column, JS.<JSHeaderCell> createArray());
                colList.add(column);
            }
        }

        for (int i = 0; i < $theadRows.size(); i++) {
            GQuery $ths = $theadRows.eq(i).children("th");

            int colOffset = 0;
            for (int j = 0; j < $ths.size(); j++) {
                JSColumn column = colList.get(j + colOffset);
                JSHeaderCell header = JS.createJsType(JSHeaderCell.class);
                GQuery $th = $ths.eq(j);
                column.setValue($th.attr("name"));
                int colSpan = 1;
                String colString = $th.attr("colspan");
                if (!colString.isEmpty()) {
                    colSpan = Integer.parseInt(colString);
                    colOffset += colSpan - 1;
                }

                // FIXME: Assuming format to be HTML, should we detect
                // between simple text and HTML contents?
                header.setColSpan(colSpan).setContent($th.html())
                        .setFormat(Format.HTML.name());
                contentsMap.get(column).add(header);
            }
        }

        Iterator<JSColumn> iterator = contentsMap.keySet().iterator();
        // When we don't use shadow, sometimes the component could
        // be renderized previously.
        lightDom.find("div[v-wc-container]").remove();

        GQuery $templateRow = lightDom.find("tr[template] td");
        for (int i = 0; iterator.hasNext(); i++) {
            JSColumn column = iterator.next();
            column.setHeaderData(contentsMap.get(column));
            if (i < $templateRow.size()) {
                String html = $templateRow.eq(i).html();
                column.setTemplate(html);
            }
        }

        setCols(colList);
    }

    public void onMutation() {
        loadHeaders();
        refresh();
    }

    public void setDisabled(boolean disabled) {
        grid.setEnabled(!disabled);
    }

    public void setEditable(boolean editable) {
        // TODO: Currently missing an editor handler
        grid.setEditorEnabled(editable);
    }

    public void setFrozenColumn(String frozenColumn) {
        Integer column = null;
        try {
            column = Integer.parseInt(frozenColumn);
        } catch (NumberFormatException e) {
            for (int i = 0; i < cols.length(); i++) {
                if (frozenColumn.equals(cols.get(i).headerData().get(0)
                        .content())) {
                    column = i + 1;
                    break;
                }
            }
        }
        if (column != null) {
            grid.setFrozenColumnCount(column);
        }
    }

    @JsNoExport
    public void setCols(JSArray<JSColumn> cols) {
        changed = true;
        this.cols = cols;
    }

    @JsNoExport
    public JSArray<JSColumn> getCols() {
        return cols;
    }

    public void setRowCount(int size) {
        // TODO: fix this in Grid, it seems this only works with reindeer
        if (Window.Location.getParameter("resize") != null && size > 0) {
            grid.setHeightMode(HeightMode.ROW);
            grid.setHeightByRows(Math.min(size,
                    RowContainer.INITIAL_DEFAULT_ROW_HEIGHT));
        }
    }

    // TODO: Remove
    @JsNoExport
    public void adjustHeight() {
        int size = grid.getDataSource().size();
        setRowCount(size);
    }

    // TODO: Remove
    @JsNoExport
    public Grid<JsArrayMixed> getGrid() {
        if (grid == null) {
            changed = true;
            initGrid();
        }
        return grid;
    }

    @JsNoExport
    @Override
    public void onSelect(SelectionEvent<JsArrayMixed> ev) {
        if (!updating) {
            setSelectedRowsProperty(getSelectedRows());
        }
    }

    private native void setSelectedRowsProperty(JsArrayInteger selected)
    /*-{
    	this.selectedRows = selected;
    }-*/;

    public void setColumnWidth(int column, int widht) {
        grid.getColumn(column).setWidth(widht);
    }

    public String getHeightMode() {
        return grid.getHeightMode().toString();
    }

    public void setHeightMode(String mode) {
        grid.setHeightMode(HeightMode.valueOf(mode));
    }

    public void setHeight(String height) {
        grid.setHeight(height);
    }

    public void setDataSource(JavaScriptObject jso, int size) {
        if (JsUtils.isFunction(jso)) {
            grid.setDataSource(new GridJsFuncDataSource(jso, size, this));
        } else if (JsUtils.isArray(jso)) {
            loadHeaders();
            grid.setDataSource(new GridJsObjectDataSource(jso
                    .<JsArray<JavaScriptObject>> cast(), this));
        } else {
            throw new RuntimeException("Unknown jso: " + jso);
        }
    }

    public void refresh() {
        if ((grid.getDataSource() instanceof GridDataSource)) {
            final JsArrayInteger a = getSelectedRows();
            ((GridDataSource) grid.getDataSource()).refresh();
            if (a.length() > 0) {
                $(this).delay(5, new Function() {
                    @Override
                    public void f() {
                        setSelectedRows(a);
                    }
                });
            }
        } else if (grid.getDataSource() != null) {
            grid.setDataSource(grid.getDataSource());
        }
    }

    public JavaScriptObject getColumns() {
        // remove old observers
        if (columnsJso != null) {
            for (int i = 0, l = columnsJso.length(); i < l; i++) {
                DOMUtils.unobserve(columnsJso.get(i));
            }
        }
        // Using GQuery data-binding magic to convert list to js arrays.
        columnsJso = cols;

        // Add observers to any column configuration object so as
        for (int i = 0, l = columnsJso.length(); i < l; i++) {
            DOMUtils.observe(columnsJso.get(i), new EventListener() {
                @Override
                public void onBrowserEvent(Event event) {
                    refresh();
                }
            });
        }
        return columnsJso;
    }

    public void setSelectionMode(String selectionMode) {
        grid.setSelectionMode(SelectionMode.valueOf(selectionMode.toUpperCase()));
    }

    public void setColumns(JavaScriptObject newCols) {
        changed = true;
        cols = newCols.cast();
    }

    public void setSelectedRows(JsArrayInteger selectedJso) {
        updating = true;
        grid.getSelectionModel().reset();
        for (int i = 0, l = selectedJso.length(); i < l; i++) {
            int selectedIndex = selectedJso.get(i);
            if (selectedIndex >= 0
                    && selectedIndex < grid.getDataSource().size()) {
                grid.select(grid.getDataSource().getRow(selectedIndex));
            }
        }
        updating = false;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public JsArrayInteger getSelectedRows() {
        JsArrayInteger selectedJso = JsArrayInteger.createArray().cast();
        selectedJso.setLength(0);
        Collection<JsArrayMixed> c = grid.getSelectedRows();
        for (Iterator<JsArrayMixed> i = c.iterator(); i.hasNext();) {
            selectedJso.push(((AbstractRemoteDataSource) grid.getDataSource())
                    .indexOf(i.next()));
        }
        return selectedJso;
    }

    public void redraw() {
        Escalator e = e(grid);
        c(e.getHeader());
        c(e.getFooter());
        c(e.getBody());
        ColumnConfiguration columnConfiguration = f(e);
        for (int i = 0; i < columnConfiguration.getColumnCount(); i++) {
            columnConfiguration.setColumnWidth(i,
                    columnConfiguration.getColumnWidth(i));
        }
    }

    private static native Escalator e(Grid<?> g)
    /*-{
        return g.@com.vaadin.client.widgets.Grid::escalator;
    }-*/;

    private static native Escalator c(RowContainer r)
    /*-{
        r.@com.vaadin.client.widgets.Escalator.AbstractRowContainer::defaultRowHeightShouldBeAutodetected = true;
        r.@com.vaadin.client.widgets.Escalator.AbstractRowContainer::autodetectRowHeightLater()();
    }-*/;

    private static native ColumnConfiguration f(Escalator e)
    /*-{
        return e.@com.vaadin.client.widgets.Escalator::columnConfiguration;
    }-*/;

}
