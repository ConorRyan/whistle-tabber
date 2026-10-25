const fs = require("node:fs");
const path = require("node:path");

const WEB = path.join(__dirname, "..", "app", "src", "main", "assets", "web");

const ABCJS = require(path.join(WEB, "abcjs-basic.js"));
const WhistleCore = require(path.join(WEB, "whistle-core.js"));

function fixture(name) {
    return fs.readFileSync(path.join(__dirname, "fixtures", name), "utf8");
}

function parse(abc) {
    return ABCJS.parseOnly(abc)[0];
}

module.exports = { ABCJS, WhistleCore, fixture, parse };
