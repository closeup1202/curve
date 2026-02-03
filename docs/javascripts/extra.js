// Custom JS for Curve Documentation
document.addEventListener("DOMContentLoaded", function() {
    // Add external link icon to external links
    var links = document.links;
    for (var i = 0, linksLength = links.length; i < linksLength; i++) {
        if (links[i].hostname != window.location.hostname) {
            links[i].target = '_blank';
            links[i].rel = 'noopener noreferrer';
        }
    }
});
