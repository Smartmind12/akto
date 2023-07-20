import { Box, Button, Divider, Icon, Text, Tooltip } from "@shopify/polaris"
import { tokens } from "@shopify/polaris-tokens"
import { InfoMinor, ClipboardMinor } from "@shopify/polaris-icons"

import { useEffect, useRef, useState } from "react";

import { editor } from "monaco-editor/esm/vs/editor/editor.api"
import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/bracketMatching/browser/bracketMatching';
import 'monaco-editor/esm/vs/editor/contrib/comment/browser/comment';
import 'monaco-editor/esm/vs/editor/contrib/codelens/browser/codelensController';
import 'monaco-editor/esm/vs/editor/contrib/colorPicker/browser/color';
import 'monaco-editor/esm/vs/editor/contrib/format/browser/formatActions';
import 'monaco-editor/esm/vs/editor/contrib/lineSelection/browser/lineSelection';
import 'monaco-editor/esm/vs/editor/contrib/indentation/browser/indentation';
import 'monaco-editor/esm/vs/editor/contrib/inlineCompletions/browser/inlineCompletionsController';
import 'monaco-editor/esm/vs/editor/contrib/snippet/browser/snippetController2'
import 'monaco-editor/esm/vs/editor/contrib/suggest/browser/suggestController';
import 'monaco-editor/esm/vs/editor/contrib/wordHighlighter/browser/wordHighlighter';
import "monaco-editor/esm/vs/basic-languages/yaml/yaml.contribution"
import { useParams } from "react-router-dom";
import TestEditorStore from "../testEditorStore";


const YamlEditor = () => {

    const testsObj = TestEditorStore(state => state.testsObj)
    
    const selectedTest = TestEditorStore(state => state.selectedTest)

    const [ editorInstance, setEditorInstance ] = useState(null);

    const yamlEditorRef = useRef(null)
    
    useEffect(()=>{        
        let Editor = null

        if (!editorInstance) {
            const yamlEditorOptions = {
                language: "yaml",
                minimap: { enabled: false },
                wordWrap: true,
                automaticLayout: true,
                colorDecorations: true,
                scrollBeyondLastLine: false,
              }
      
              editor.defineTheme('subdued', {
                  base: 'vs',
                  inherit: true,
                  rules: [],
                  colors: {
                      'editor.background': '#FAFBFB',
                  },
              });
              
              editor.setTheme('subdued')
      
              Editor = editor.create(yamlEditorRef.current, yamlEditorOptions)
              setEditorInstance(Editor)
        } else {
            Editor = editorInstance
        }

        if (selectedTest) {
            const value = testsObj.mapTestToData[selectedTest.label].content
            Editor.setValue(value)
        }
    
      }, [selectedTest])

    const copyContent = () =>{
        console.log("copy")
    }

    return (
        <div style={{ borderWidth: "0px, 1px, 1px, 0px", borderStyle: "solid", borderColor: "#E1E3E5"}}>
            <div style={{display: "grid", gridTemplateColumns: "max-content max-content max-content auto max-content", gap: "5px",  alignItems: "center", background: tokens.color["color-bg-app"], height: "10vh", padding: "10px"}}>
                <Text variant="bodyMd">{selectedTest.label}</Text>
                <Tooltip content={`Last Updated ${testsObj.mapTestToData[selectedTest.label].lastUpdated}`} preferredPosition="above" dismissOnMouseOut>
                    <Icon source={InfoMinor} color="subdued"/> 
                </Tooltip>
                <Tooltip content="Copy Content" dismissOnMouseOut preferredPosition="above">
                    <Button icon={ClipboardMinor} plain onClick={copyContent} />
                </Tooltip>       
                <div />
                <Button>Save</Button>
            </div>
            <Divider />
            <Box ref={yamlEditorRef} minHeight="80vh">
            </Box>
        </div>
    )
}

export default YamlEditor