package com.fmp.inspector;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Base64;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.StringWriter;
import org.xmlpull.v1.XmlSerializer;

/** Test-only package: read the real active window without waiting for changing video time labels. */
public final class SnapshotRunner extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            UiAutomation automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            android.accessibilityservice.AccessibilityServiceInfo service = automation.getServiceInfo();
            service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            automation.setServiceInfo(service);
            AccessibilityNodeInfo root = null;
            for (int i = 0; i < 20 && root == null; i++) {
                root = automation.getRootInActiveWindow();
                if (root == null) Thread.sleep(100);
            }
            if (root == null) throw new IllegalStateException("No active UI window");
            StringWriter output = new StringWriter();
            XmlSerializer xml = Xml.newSerializer();
            xml.setOutput(output);
            xml.startTag(null, "hierarchy");
            write(xml, root);
            xml.endTag(null, "hierarchy");
            xml.flush();
            result.putString("tree", Base64.encodeToString(output.toString().getBytes("UTF-8"), Base64.NO_WRAP));
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("error", error.toString());
            finish(1, result);
        }
    }
    private void write(XmlSerializer xml, AccessibilityNodeInfo node) throws Exception {
        if (!node.isVisibleToUser()) return;
        xml.startTag(null, "node");
        xml.attribute(null, "text", value(node.getText()));
        xml.attribute(null, "content-desc", value(node.getContentDescription()));
        xml.attribute(null, "resource-id", value(node.getViewIdResourceName()));
        xml.attribute(null, "class", value(node.getClassName()));
        xml.attribute(null, "focused", String.valueOf(node.isFocused()));
        xml.attribute(null, "checkable", String.valueOf(node.isCheckable()));
        xml.attribute(null, "checked", String.valueOf(node.isChecked()));
        xml.attribute(null, "clickable", String.valueOf(node.isClickable()));
        xml.attribute(null, "enabled", String.valueOf(node.isEnabled()));
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        xml.attribute(null, "bounds", "["+bounds.left+","+bounds.top+"]["+bounds.right+","+bounds.bottom+"]");
        for (int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { write(xml, child); child.recycle(); }
        }
        xml.endTag(null, "node");
    }
    private static String value(CharSequence value) { return value == null ? "" : value.toString(); }
}
