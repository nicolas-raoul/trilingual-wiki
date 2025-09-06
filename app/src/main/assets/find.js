var currentMatch = 0;
var totalMatches = 0;

function highlightAll(searchTerm) {
    // First, remove any existing highlights
    var highlighted = document.querySelectorAll('.btf-highlight');
    highlighted.forEach(function(element) {
        var parent = element.parentNode;
        parent.replaceChild(document.createTextNode(element.textContent), element);
        parent.normalize(); //  merges adjacent text nodes
    });

    if (!searchTerm) {
        if (window.Find) {
            window.Find.onFindResult(0, 0);
        }
        return;
    }

    var textNodes = [];
    var treeWalker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
    while (treeWalker.nextNode()) {
        textNodes.push(treeWalker.currentNode);
    }

    var regex = new RegExp(searchTerm, "gi");
    totalMatches = 0;

    textNodes.forEach(function(node) {
        var matches = node.textContent.match(regex);
        if (matches) {
            totalMatches += matches.length;
            var newNode = document.createElement('span');
            newNode.innerHTML = node.textContent.replace(regex, function(match) {
                return '<span class="btf-highlight">' + match + '</span>';
            });
            node.parentNode.replaceChild(newNode, node);
        }
    });

    if (window.Find) {
        window.Find.onFindResult(totalMatches, 0);
    }

    if (totalMatches > 0) {
        highlightNext(true, true); // highlight first match
    }
}

function highlightNext(forward, first) {
    var highlights = document.querySelectorAll('.btf-highlight');
    if (highlights.length === 0) {
        return;
    }

    if (!first) {
        highlights[currentMatch].classList.remove('btf-highlight-active');
        if (forward) {
            currentMatch = (currentMatch + 1) % highlights.length;
        } else {
            currentMatch = (currentMatch - 1 + highlights.length) % highlights.length;
        }
    } else {
        currentMatch = 0;
    }

    var activeNode = highlights[currentMatch];
    activeNode.classList.add('btf-highlight-active');
    activeNode.scrollIntoView({ behavior: 'smooth', block: 'center' });

    if (window.Find) {
        window.Find.onFindResult(totalMatches, currentMatch);
    }
}

// Inject CSS
var style = document.createElement('style');
style.innerHTML = `
    .btf-highlight {
        background-color: yellow;
        color: black;
    }
    .btf-highlight-active {
        background-color: orange;
        color: white;
    }
`;
document.head.appendChild(style);
