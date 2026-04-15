package com.github.t1.openapi.ui.components;

import com.github.t1.htmljava.AbstractElement;

import static com.github.t1.htmljava.HtmlBasics.span;

/// A tab bar that emits a `tabchange` CustomEvent when the active tab changes.
/// ArrowLeft/Right switches tabs internally (with stopPropagation).
/// ArrowUp/Down bubbles to spatial navigation.
public class TabBar extends AbstractElement<TabBar> {

    public static TabBar tabBar(String name) {return new TabBar(name);}

    private TabBar(String name) {
        super("div");
        attr("data-tab-bar", name);
        attr("tabindex", "0");
    }

    public TabBar tab(String value, String label) {return addTab(value, label, false);}

    public TabBar activeTab(String value, String label) {return addTab(value, label, true);}

    private TabBar addTab(String value, String label, boolean active) {
        var item = span(label).attr("data-tab-value", value);
        if (active) item.classes("is-active");
        content(item);
        return this;
    }

    public static String js() {return JS;}

    private static final String JS = """
            // TabBar: ArrowLeft/Right to switch active tab.
            // At boundaries (first/last tab), the event is NOT stopped — it continues
            // to the global spatial nav handler which navigates to the nearest element.
            document.addEventListener('keydown', function(e) {
                var tabBar = e.target.closest('[data-tab-bar]');
                if (!tabBar) return;
                if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
                var items = Array.from(tabBar.querySelectorAll('[data-tab-value]'));
                var activeIndex = items.findIndex(function(t) {
                    return t.classList.contains('is-active')
                        || (t.closest('li') && t.closest('li').classList.contains('is-active'));
                });
                var next = e.key === 'ArrowRight'
                    ? Math.min(activeIndex + 1, items.length - 1)
                    : Math.max(activeIndex - 1, 0);
                if (next === activeIndex) {
                    // At left boundary: return to tree (bidirectional with tree's ArrowRight→tabs)
                    if (e.key === 'ArrowLeft') {
                        var tree = document.querySelector('[role="tree"]');
                        if (tree) {
                            e.preventDefault();
                            e.stopImmediatePropagation();
                            tree.focus();
                        }
                    }
                    // At right boundary: let event continue to spatial nav
                    return;
                }
                e.preventDefault();
                e.stopImmediatePropagation();
                items.forEach(function(t) {
                    t.classList.remove('is-active');
                    var li = t.closest('li');
                    if (li) li.classList.remove('is-active');
                });
                items[next].classList.add('is-active');
                var li = items[next].closest('li');
                if (li) li.classList.add('is-active');
                tabBar.dispatchEvent(new CustomEvent('tabchange', {
                    bubbles: true,
                    detail: { value: items[next].getAttribute('data-tab-value') }
                }));
            });
            """;
}
