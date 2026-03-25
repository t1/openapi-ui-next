package com.github.t1.openapi.ui.components;

import com.github.t1.htmljava.AbstractElement;
import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;

import static com.github.t1.htmljava.HtmlBasics.div;

public class SplitPane extends AbstractElement<SplitPane> {
    private final Element firstPanel = div().classes("split-first");
    private final Element secondPanel = div().classes("split-second");

    public static SplitPane splitPane() { return new SplitPane(); }

    private SplitPane() {
        super("div");
        classes("split-layout");
        content(firstPanel);
        content(div().classes("split-handle"));
        content(secondPanel);
    }

    public SplitPane first(Renderable content) {
        firstPanel.content(content);
        return this;
    }

    public SplitPane second(Renderable content) {
        secondPanel.content(content);
        return this;
    }

    public SplitPane ratio(int first, int second) {
        style("grid-template-columns: minmax(150px, " + first + "fr) 0px minmax(150px, " + second + "fr)");
        return this;
    }

    public SplitPane persistAs(String key) {
        attr("data-persist", key);
        return this;
    }

    public static String css() { return CSS; }

    public static String js() { return JS; }

    private static final String CSS = """
            .split-layout {
                display: grid;
                grid-template-columns: minmax(150px, 1fr) 0px minmax(150px, 2fr);
                gap: 0;
            }
            .split-handle {
                width: 14px;
                margin-left: -7px;
                margin-right: -7px;
                cursor: col-resize;
                background: transparent;
                position: relative;
                z-index: 1;
            }
            .split-handle::after {
                content: '\\2022\\a\\2022\\a\\2022\\a\\2022\\a\\2022\\a\\2022\\a\\2022';
                white-space: pre;
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-150%, -50%);
                color: var(--bulma-text-weak);
                font-size: 0.6rem;
                line-height: 0.7;
                opacity: 0.7;
                transition: opacity 0.15s;
            }
            .split-handle:hover::after {
                opacity: 1;
                color: var(--bulma-link);
            }
            @media screen and (max-width: 1023px) {
                .split-layout {
                    grid-template-columns: 1fr;
                }
                .split-handle {
                    display: none;
                }
            }
            """;

    private static final String JS = """
            function initSplitPanes(root) {
                (root || document).querySelectorAll('.split-layout').forEach(function(splitLayout) {
                    if (splitLayout.dataset.splitInit) return;
                    splitLayout.dataset.splitInit = 'true';
                    var splitHandle = splitLayout.querySelector('.split-handle');
                    if (!splitHandle) return;
                    var persistKey = splitLayout.getAttribute('data-persist');
                    if (persistKey) {
                        var savedWidth = localStorage.getItem(persistKey);
                        if (savedWidth) {
                            splitLayout.style.gridTemplateColumns = savedWidth + 'px 0px 1fr';
                        }
                    }
                    splitHandle.addEventListener('pointerdown', function(e) {
                        e.preventDefault();
                        var splitFirst = splitLayout.querySelector('.split-first');
                        var startX = e.clientX;
                        var startWidth = splitFirst.getBoundingClientRect().width;
                        function onMove(e) {
                            var newWidth = Math.max(150, startWidth + e.clientX - startX);
                            var maxWidth = splitLayout.getBoundingClientRect().width - 150;
                            newWidth = Math.min(newWidth, maxWidth);
                            splitLayout.style.gridTemplateColumns = newWidth + 'px 0px 1fr';
                        }
                        function onUp() {
                            document.removeEventListener('pointermove', onMove);
                            document.removeEventListener('pointerup', onUp);
                            if (persistKey) {
                                var finalWidth = splitFirst.getBoundingClientRect().width;
                                localStorage.setItem(persistKey, Math.round(finalWidth));
                            }
                        }
                        document.addEventListener('pointermove', onMove);
                        document.addEventListener('pointerup', onUp);
                    });
                });
            }
            document.addEventListener('DOMContentLoaded', function() { initSplitPanes(); });
            document.body.addEventListener('htmx:afterSettle', function(e) { initSplitPanes(e.detail.elt); });
            """;
}
