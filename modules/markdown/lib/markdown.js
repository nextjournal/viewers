let MarkdownIt = require('markdown-it'),
    MD = new MarkdownIt({html: true, linkify: true});

let texmath = require('markdown-it-texmath')
MD.use(texmath, {delimiters: "dollars"})

let blockImage = require("markdown-it-block-image")
MD.use(blockImage)

let mdToc = require("markdown-it-toc-done-right")
MD.use(mdToc)

function parseJ(text) { return JSON.stringify(MD.parse(text, {})) }

function parse(text)  { return MD.parse(text, {}) }
module.exports = {parseJ: parseJ, parse: parse} ;
