import io
import os

ROOTS = ['app/src', 'libs', 'buildSrc/src', 'xtask/src', 'wekit-zygisk']
EXTS = {'.kt', '.java', '.kts', '.xml', '.rs', '.toml', '.pro', '.txt',
        '.prop', '.sh', '.list', '.gradle'}
# Build output must not be rewritten; it is regenerated anyway.
SKIP = ('/build/', '/bin/', '/target/', '/.gradle/')

REPLACEMENTS = [
    ('dev.ujhhgtg.wekit', 'dev.wick'),
    ('dev/ujhhgtg/wekit', 'dev/wick'),
    ('dev.ujhhgtg.reflekt', 'dev.wick.reflekt'),
    ('dev/ujhhgtg/reflekt', 'dev/wick/reflekt'),
]

changed = 0
for root in ROOTS:
    if not os.path.isdir(root):
        continue
    for dirpath, _dirnames, files in os.walk(root):
        norm = dirpath.replace(os.sep, '/') + '/'
        if any(s in norm for s in SKIP):
            continue
        for name in files:
            if os.path.splitext(name)[1] not in EXTS:
                continue
            path = os.path.join(dirpath, name)
            try:
                text = io.open(path, encoding='utf-8').read()
            except (UnicodeDecodeError, OSError):
                continue
            original = text
            for old, new in REPLACEMENTS:
                text = text.replace(old, new)
            if text != original:
                io.open(path, 'w', encoding='utf-8', newline='\n').write(text)
                changed += 1

print('files changed:', changed)
