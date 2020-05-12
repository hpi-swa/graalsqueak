# Images for TruffleSqueak

## TruffleSqueak Image Creation

Run the following in a workspace, then save and quit the image:

```smalltalk
Metacello new
    baseline: 'TruffleSqueak';
    repository: 'github://hpi-swa/trufflesqueak:image/src';
    load: #('tests').
(Smalltalk at: #TruffleSqueakUtilities) setupImage.
```

## TruffleSqueak Test Image Creation

Run the following in a workspace, then save and quit the image:

```smalltalk
Metacello new
    baseline: 'TruffleSqueak';
    repository: 'github://hpi-swa/trufflesqueak:image/src';
    load: #('tests').
(Smalltalk at: #TruffleSqueakUtilities) setupTestImage.
```
