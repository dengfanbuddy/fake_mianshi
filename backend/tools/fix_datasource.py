import io

for p in ['src/main/resources/application.yml', 'src/test/resources/application.yml']:
    src = io.open(p, encoding='utf-8').read()
    src = src.replace('jdbc:sqlite:./data/fake_mianshi.db',
                      'jdbc:sqlite:./data/fake_mianshi.db?busy_timeout=30000&journal_mode=WAL')
    src = src.replace('jdbc:sqlite:${java.io.tmpdir}/fm_test_mianshi.db',
                      'jdbc:sqlite:${java.io.tmpdir}/fm_test_mianshi.db?busy_timeout=30000&journal_mode=WAL')
    io.open(p, 'w', encoding='utf-8', newline='').write(src)
    print('patched', p)
