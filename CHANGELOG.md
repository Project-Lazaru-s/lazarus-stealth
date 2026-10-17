# Changelog

## 0.1.1 — 2026-10-17

- Novo nível de ruído **Extremo** (alcance de 40 blocos ou mais), reservado aos tiros do TaCZ sem silenciador. Tiro silenciado continua **Alto**. Nenhum passo alcança Extremo, e o alcance e a reação dos zumbis não mudaram: o nível só separa, no indicador e na prioridade das pistas, o tiro da corrida em metal ou vidro.
- Indicador redesenhado: ícone de alto-falante com 0 a 4 ondas, semitransparente, no canto inferior esquerdo (`offsetLeft` 14 e `offsetBottom` 50 no config do cliente). Sem texto.

## 0.1.0 — 2026-10-17

Primeira versão.

- Percepção por par infectado–jogador: Desatento, Desconfiado, Investigando, Alerta e Perseguindo. Só o estado Perseguindo deixa um infectado ter o jogador como alvo, seja qual for o mod que tente dar o alvo (vanilla, Zombie Awareness, Dual Hitbox, Apocalypse Now, Undead Revamped).
- Visão com cone frontal e periferia, raios de oclusão por parte do corpo (blocos `see_through` não escondem), luz, postura real (agachado e rastejando, inclusive o rastejar forçado do Smart Moving), velocidade e habilidade. A suspeita acumula com o tempo.
- Memória: última posição conhecida (6 s), busca local (8 s) e calma gradual. Agachar não encerra uma perseguição.
- Audição: ruídos levam o infectado ao ponto do som, nunca ao jogador. Paredes abafam e há limite de reações por ruído.
- Passos por distância, superfície (tags `surface_soft`, `surface_metal`, `surface_glass`), postura, carga (Encumbered) e exaustão (Lazarus Moodles). Também fazem ruído: quedas, respiração, portas, contêineres, coleta, golpes, quebra de blocos e de vidro e tiros do TaCZ.
- Finalização furtiva no ataque corpo a corpo normal, validada no servidor. Mata infectados comuns e dá crítico controlado nos especiais. Gasta fôlego do PeakStamina, tem recarga e preserva crédito, drops e estatísticas. Nos zumbis do Dual Hitbox vira golpe na cabeça.
- Árvore **Furtividade** no Puffish Skills (4 níveis, 4 habilidades por atributos) com XP por evasão, investigação despistada e finalização, e proteção contra farm.
- Indicador de ruído Nenhum/Baixo/Médio/Alto. O protocolo já leva o estado de ameaça para um indicador futuro.
- Comandos `/lazarusstealth debug` e `/lazarusstealth status`.
- Requer Lazarus Moodles 1.15.1 ou mais novo (nova API `LazarusMoodlesApi.exhaustionLevel`), se o Moodles estiver instalado.
