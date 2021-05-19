
import java.util.regex.Matcher
import java.text.Collator

import org.dict_uk.expand.*


def AFFIX_DIR = "../../data/affix"

Args.args = new Args()
Args.args.removeWithTags = ["uncontr"]

def expand = new Expand(false)

def affixMap = expand.affix.load_affixes(AFFIX_DIR)

//Affix affixLoader = new Affix()
//affixLoader.load_affixes(AFFIX_DIR)

char hunFlag = (int)' '+1

// we're running out of hunspell flags, need to compact some that don't overlap
def reMapFlags = [
    'vr': 'v',
    'adj_ev': 'n40',
    'adj_pron': 'n40',
    'numr': 'n40',
    'n2adj3': 'n40'
]


def flagMap = [:].withDefault{ [] }
def revFlagMap = [:]
def toHunFlagMap = [:]
def negativeMatchFlags = [:].withDefault{ [] }

affixMap.each { flag, affixGroupMap ->
    reMapFlags.each { k,v ->
        if( flag.startsWith(k) ) {
            if ( affixMap[ flag.replace(k, v) ] ) {
                affixMap[ flag.replace(k, v) ].putAll(affixGroupMap)
            }
        }
    }
}

def reMapFlagsRe = reMapFlags.keySet().join("|")

affixMap.each { flag, affixGroupMap ->
	if( flag == 'patr' )
		return

	if( flag =~ /\.ku|n2[0-9].*\.u|patr_pl|shrt|/ + reMapFlagsRe )
		return

	if( (int)hunFlag == 0x7F ) {
	    System.err.println("WARNING: using hunspel flag > 0x7F, this may not work")
	}

	//	println flag + " = " + affixGroupMap

	affixGroupMap.each { ending, affixGroup ->

		flagMap[ hunFlag ] << [ending: ending, affixGroup: affixGroup]

		if( affixGroup.neg_match ) {
			negativeMatchFlags[flag] << affixGroup
		}

		//		flagMap[ [flag: flag, ending: affixGroup.match] ]
	}

	revFlagMap[hunFlag] = flag
	toHunFlagMap[flag] = hunFlag

	hunFlag = (char)((int)hunFlag+1)
	if("#/".contains(''+hunFlag)) {
		hunFlag = (char)((int)hunFlag+1)
	}

}

new File('build/mapping.txt').text = revFlagMap*.toString().join("\n")

println("Negative matches:\n\t" + negativeMatchFlags*.toString().join("\n\t"))

def NONSPELL_TAG_LIST = ":(alt|bad|subst|slang|short|long)"

if( "-forSearch" in args ) {
    println "Дозволяємо ненормативні форми для пошуку..."
    NONSPELL_TAG_LIST.replace('|bad', '')
    NONSPELL_TAG_LIST.replace('|subst', '')
    NONSPELL_TAG_LIST.replace('|slang', '')
}

def NONSPELL_TAGS = ~ NONSPELL_TAG_LIST


def out = ''
flagMap.each{ flag, affixGroupItems ->

	def dictUkFlag = revFlagMap[flag]
	def body = ''
	def cnt = 0
	affixGroupItems.each { item ->

//		if( item.affixGroup.neg_match ) {
//			println "TODO: not handling negative match $item.affixGroup.neg_match for " + item.affixGroup.match + " in " + revFlagMap[flag]
//		}

		item.affixGroup.affixes.each { affix ->

			if( NONSPELL_TAGS.matcher(affix.tags) ) {
//					println 'Skipping uncontr: ' + affix
				return
			}


			if( affix.fromm && ! (affix.fromm ==~ /[а-яіїєґА-ЯІЇЄҐ']+/ ) ) {
//				println "regex in $flag ("+revFlagMap[flag]+") $affix.fromm -> $affix.to @ $item.ending"

				Matcher matcher = item.ending =~ /\[([а-яіїєґА-ЯІЇЄҐ']+|\^жш)\]/

				if( ! matcher || matcher.size() > 2 ) {
//					println "TODO: not handling $item.ending /$affix.fromm -> $affix.to/ for $item.edning in " + dictUkFlag
					return
				}

				if( matcher.size() == 2 && (! affix.fromm.startsWith('.') || affix.fromm.contains('(')) ) {
//				println "regex in $flag ("+revFlagMap[flag]+") $affix.fromm -> $affix.to @ $item.ending"

					matcher[0][1].toCharArray().each { it1 ->
						matcher[1][1].toCharArray().each { it2 ->
							def fromm
							def to

							if( affix.fromm.contains('(..)') ) {
								fromm = affix.fromm.replace('(..)', ''+it1 + it2)
								to = affix.to.replaceFirst(/\$1/, ''+it1 + it2)
							}
							else if( affix.fromm.contains('.(.)') ) {
								fromm = affix.fromm.replace('.(.)', ''+it1 + it2)
								to = affix.to.replaceFirst(/\$1/, ''+it2)
							}
							else {
// 								println "TODO: not handling $item.ending /$affix.fromm -> $affix.to/ for " + dictUkFlag
								return
							}

							def ending = item.ending.replaceFirst(/\[/+matcher[0][1]+/\]/, ''+it1)
							ending = ending.replaceFirst(/\[/+matcher[1][1]+/\]/, ''+it2)
//													println "\t $fromm -> $to @ $ending"

//    					    println "\t $item.ending -> $ending / " + matcher[0] + " / " + matcher[1]

				if( affix.to.contains('$2') ) {
//					println '[2] Skipping complex regex: ' + affix
					return
				}


							body += "SFX $flag $fromm $to $ending"
							body += '\n'
							cnt += 1
						}
					}
					return

				}

//				if( matcher.size() == 2 && affix.fromm.startsWith('.') && ! affix.fromm.contains('(') ) {
//					println "regex in $flag ("+revFlagMap[flag]+") $affix.fromm -> $affix.to @ $item.ending"
//				}

				def matcherGroup = matcher.size()==1 ? matcher[0][1] : matcher[1][1]

				if( matcherGroup == "^жш" ) {
					System.err.println("Manual case for [^жш]")
					matcherGroup = "бвгнпрст"
				}

				matcherGroup.toCharArray().each {
					def fromm
					if( affix.fromm.contains('(.)') ) {
						fromm = affix.fromm.replaceFirst(/\(\.\)/, ''+it)
					}
					else if (affix.fromm.contains('.') ) {
						fromm = affix.fromm.replaceFirst(/\./, ''+it)
					}
					else if (affix.fromm.contains(matcher[0][0])) {
						fromm = affix.fromm.replace(matcher[0][0], ''+it)
					}
					else if ( ! affix.fromm ) {
						fromm = '0'
					}

					def to = affix.to ? affix.to.replaceFirst(/\$1/, ''+it) : 0
					def ending = item.ending.replaceFirst(/\[($matcherGroup|\^жш)\]/, ''+it)

//					if( matcher.size() == 2 ) {
//						println "\t $fromm -> $to @ $ending"
//					}


				if( affix.to.contains('$2') ) {
//					println '[1] Skipping complex regex: ' + affix
					return
				}


					body += "SFX $flag $fromm $to $ending"
					body += '\n'
					cnt += 1
				}

			}
			else {
//				if( dictUkFlag affix.tags =~ ':v_kly' && dictUkFlag )

                if( dictUkFlag.startsWith('adj') && item.affixGroup.match == 'лиций' )
                    return

				def fromm = affix.fromm ? affix.fromm : '0'
				def to = affix.to ? affix.to : '0'
				def ending = item.affixGroup.match


				body += "SFX $flag $fromm $to $ending"
				body += '\n'
				cnt += 1
			}

		}
	}

	if( cnt ) {
		def header = "SFX $flag Y $cnt" //\t\t# " + revFlagMap[flag]
		out += header
		out += '\n'
		out += body
	}
	else {
		System.err.println "NOTE: empty flag: $flag from " + revFlagMap[flag]
	}
}

def fileHeader = new File('header/affix_header.txt').text
new File('build/hunspell/uk_UA.aff').text = fileHeader + '\n' + out



// word list


def dictDir = new File("../../data/dict")

def IGNORED_FILES = /composite|dot-abbr|twisters|invalid|alt|rare|add_tag/
if( "-forSearch" in args ) {
    IGNORED_FILES = IGNORED_FILES.replace('|twisters|invalid', '')
}

def files = dictDir.listFiles().findAll {
    it.name.endsWith('.lst') && ! (it.name =~ IGNORED_FILES )
}

println("Dict files: " + files*.name)


def MULTIFLAG_PATTERN = ~ ' /[nv][^#]+ /[nv]'

def superlatives = []

def lines = files.collect { file->
	def lns = file.readLines()
	if( file.name =~ /geo-.*\.lst/ ) {
	    lns = lns.collect{ Character.isUpperCase(it.charAt(0)) ? it + '   #@ :prop' : it }
	}
	lns
}
.flatten()
.collect {
	if( MULTIFLAG_PATTERN.matcher(it) ) {
//		System.err.println('got dual flag ' + it)
		def parts = it.split(' ', 4)
		def extra = parts.size() > 3 ? ' ' + parts[3] : ''
		return [parts[0] + ' ' + parts[1] + extra, parts[0] + ' ' + parts[2] + extra]
	}
	else if( it.contains('.patr') ) {
		return expand.preprocess(new LineGroup(it)).collect { LineGroup lg -> lg.line }
	}
	return it
}
.flatten()
.collect {
    def propName = it.contains('#@ :prop')

	it = it.replaceFirst(/ *#.*/, '').trim()

	if( ! it )
		return ''

    if( it =~ NONSPELL_TAG_LIST )
        return ''

	assert ! it.contains('patr'), "patr in $it !!!"

	if( ! it.contains(" /") ) {
		if( it.startsWith("+cs") ) {
			it = it.replaceFirst(/\+cs=([^ ]*).*/, '$1/' + toHunFlagMap['adj'])
			def superBase = it.startsWith("най") ? it[3..-1] : it
			superlatives << 'най' + superBase
			superlatives << 'щонай' + superBase
			superlatives << 'якнай' + superBase
			superlatives << 'щоякнай' + superBase
		}
		else {
			it = it.split()[0]
		}
		return it
	}
	else if( it.contains("/<") ) {
		it = it.split()[0]
		return it
	}
	else {
		def parts = it.split(' ', 3)

		if( (it.contains(".<") && ! it.contains("<+")) || hasInanimVkly(it, propName) ) {
		    if( parts[1].contains("/n10") || parts[1].contains("/n3") ) {
			    if( ! parts[1].contains(".k") && ! parts[0].contains("ще ") ) {
				    parts[1] += parts[1].contains("/n10") ? ".ko" : ".ke"
			    }
		    }
		    else if( parts[1] =~ '/n2[0-4]' && ! parts[1].contains(".k") ) {
			    if( Expand.isDefaultKlyE(parts[0], parts[1]) ) {
			        parts[1] += ".ke"
		        }
		    }
        }
        else if( it =~ "[гкр] /" && (! it.contains(".<") && ! hasInanimVkly(it, propName)) && it.contains(".ke") ) {
           parts[1] = parts[1].replace(".ke", "")
        }

		if( parts[1].contains(".ikl") ) {
		    it = it.replace(".ikl", '')
		    parts[1] = parts[1].replace(".ikl", '')
		}

		if( parts[1] =~ /n10.*\.<\+?m/ ) {
			parts[1] = parts[1].replaceFirst(/\.<\+?m/, '')
			it = it.replaceFirst(/\.<\+?m/, '')
//			println "-- n10m: $it"
		}


		def allFlags = parts[1][1..-1]

        reMapFlags.each { k,v ->
        	if( allFlags.startsWith(k) ) {
    			allFlags = allFlags.replace(k, v)
            }
		}

		def flgs = allFlags.split(/\./)

		def mainFlg = flgs[0]

		def outFlags = ''


		if( mainFlg in negativeMatchFlags
				&& negativeMatchFlags[mainFlg].find{ parts[0] =~ it.neg_match+/$/ }
			    || it =~ " [gp]=|/adj\\.<\\+|<\\+m|\\.pzi| /.* /"
					) {

//			if( ! (it =~ /adj\.<|n2adj2\.<|p=/ ) ) {
//				println "-- manual expand for $parts"
//			}

//			def expandFlags = parts.size() > 2 ? parts[1] + ' ' + parts[2] : parts[1]
//            def expanded = expand.expand(parts[0], expandFlags)

            def expanded = expand.preprocess2(it).collect { ln ->
                def pp = ln.split(/ /, 2)
                expand.expand(pp[0], pp[1])
            }
            .flatten()


			def uniqForms = expanded.findAll{
			    ! (it =~ /:(alt|bad|subst|slang|short|long)/ )
			}.collect {
			    it.word
			}.unique() // + ' # TODO: inflect'

			return uniqForms
		}
		else {
			flgs.each { flg ->
				if( flg.startsWith('<') || flg == 'u' || flg == 'ku' || flg == '@' )
					return

				def f = flg == mainFlg ? flg : mainFlg + '.' + flg
//				if( flg == 'patr' ) {
//					f = 'n.patr'
//				}
//				else
				if( f.contains('.cf') || f.contains('.is') ) {
					f = f.replaceFirst('v[0-9]', 'v')
				}
				else if( f == 'v3.advp' ) {
					f = 'v1.advp'
				}
				else if( f == 'v3.it0' ) {
					f = 'v1.it0'
				}


				if( ! toHunFlagMap.containsKey(f) ) {
					println 'cant find ' + parts[0] + '/' + f
				}

				outFlags += toHunFlagMap[f]
			}

			return parts[0] + '/' + outFlags
		}
	}


	if( it ) {
		it = it.replaceFirst(/[. ].*/, '')
	}
}.grep {
	it
}.flatten()


lines += superlatives

def lineMap = [:]
lines.each {
    if( ! it.contains('/') ) {
        if( ! (it in lineMap) ) {
            lineMap[it] = null
        }
        return
    }

    def (word, flags) = it.split('/')
    if( word in lineMap ) {
        lineMap[word] = (Arrays.asList(lineMap[word].toCharArray()) + Arrays.asList(flags.toCharArray())).unique().join()
    }
    else {
        lineMap[word] = flags
    }
}

lines = lineMap.collect { k,v ->
    v ? k +'/' + v : k
}

lines.addAll(superlatives)

def extraWords = getClass().getResource( '/extra_words.txt' ).text

lines.addAll(extraWords.split())

def comps = []

def dic_file = new File("../../data/dict").eachFile{ file ->
    if( ! file.name.contains("composite") )
        return

    def expand_comps = new ExpandComps(expand)

    def dic_file_reader = file.withReader("utf-8") { reader ->
        comps += expand_comps.process_input(reader.readLines())
    }
}

println "Got $comps.size comps"


comps = comps.findAll{ ! (it =~ NONSPELL_TAG_LIST + /|inanim:.:v_kly/ ) }

lines.addAll(comps.collect{ it.word })


println "Found ${lines.size} total lines"

def words = lines

def txt = "${words.size}\n"


Collator collator = Collator.getInstance(new Locale("uk", "UA"));

txt += words.toSorted(collator).join("\n")

new File("build/hunspell/uk_UA.dic").text = txt


static boolean hasInanimVkly(line, propName) {
    return propName || line.contains('.ikl')
}
